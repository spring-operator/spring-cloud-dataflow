/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.server.service.impl.validation;

import java.io.IOException;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.dataflow.registry.support.AppResourceCommon;
import org.springframework.cloud.dataflow.registry.support.DockerImage;
import org.springframework.cloud.dataflow.server.DockerValidatorProperties;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Provides operations to query the Docker repository for tags for a given
 * image URI.
 *
 * @author Glenn Renfro
 * @author Chris Schaefer
 */
public class DockerRegistryValidator {
	private static final Logger logger = LoggerFactory.getLogger(DockerRegistryValidator.class);
	private static final String DOCKER_REGISTRY_AUTH_TYPE = "JWT";
	private static final String DOCKER_REGISTRY_TAGS_PATH = "/%s/tags/";
	private static final String USER_NAME_KEY = "username";
	private static final String PASSWORD_KEY = "password";
	private final AppResourceCommon appResourceCommon;

	private DockerAuth dockerAuth;
	private RestTemplate restTemplate;

	private DockerResource dockerResource;
	private DockerValidatorProperties dockerValidatiorProperties;

	public DockerRegistryValidator(DockerValidatorProperties dockerValidatorProperties,
			DockerResource dockerResource) {
		this.dockerValidatiorProperties = dockerValidatorProperties;
		this.dockerResource = dockerResource;
		this.restTemplate = configureRestTemplate();
		this.dockerAuth = getDockerAuth();
		this.appResourceCommon =  new AppResourceCommon(new MavenProperties(), null);
	}

	/**
	 * Verifies that the image is present.
	 *JobDependencies.java
	 * @return true if image is present.
	 */
	public boolean isImagePresent() {
		boolean result = false;
		try {
			DockerResult dockerResult = getDockerImageInfo();
			String resourceTag = this.appResourceCommon.getResourceVersion(this.dockerResource);
			if (dockerResult.getCount() > 0) {
				for (DockerTag tag : dockerResult.getResults()) {
					if (tag.getName().equals(resourceTag)) {
						result = true;
						break;
					}
				}
			}
		}
		catch (HttpClientErrorException hcee) {
			//when attempting to access an invalid docker image or if you
			//don't have proper credentials docker returns a 404.
			logger.info("Unable to find image because of the following exception:", hcee);
			result = false;
		}
		return result;
	}

	private RestTemplate configureRestTemplate() {
		CloseableHttpClient httpClient
				= HttpClients.custom()
				.setSSLHostnameVerifier(new NoopHostnameVerifier())
				.build();
		HttpComponentsClientHttpRequestFactory requestFactory
				= new HttpComponentsClientHttpRequestFactory();
		requestFactory.setHttpClient(httpClient);
		requestFactory.setConnectTimeout(dockerValidatiorProperties.getConnectTimeoutInMillis());
		requestFactory.setReadTimeout(dockerValidatiorProperties.getReadTimeoutInMillis());

		RestTemplate restTemplate = new RestTemplate(requestFactory);
		return restTemplate;
	}

	private DockerAuth getDockerAuth() {
		DockerAuth result = null;
		String userName = dockerValidatiorProperties.getUserName();
		String password = dockerValidatiorProperties.getPassword();
		if (StringUtils.hasText(userName) && password != null) {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<String> httpEntity;
			JSONObject request = new JSONObject();
			try {
				request.put(USER_NAME_KEY, userName);
				request.put(PASSWORD_KEY, password);
			}
			catch (JSONException ie) {
				throw new IllegalStateException(ie);
			}
			httpEntity = new HttpEntity<>(request.toString(), headers);
			ResponseEntity dockerAuth = restTemplate.exchange(
					dockerValidatiorProperties.getDockerAuthUrl(),
					HttpMethod.POST, httpEntity, DockerAuth.class);
			result = (DockerAuth) dockerAuth.getBody();
		}
		return result;
	}

	private DockerResult getDockerImageInfo() {
		HttpHeaders headers = new HttpHeaders();
		if (this.dockerAuth != null) {
			headers.add(HttpHeaders.AUTHORIZATION, DOCKER_REGISTRY_AUTH_TYPE + " " + this.dockerAuth.getToken());
		}
		HttpEntity<String> httpEntity = new HttpEntity<>(headers);
		ResponseEntity tags = this.restTemplate.exchange(getDockerTagsEndpointUrl(), HttpMethod.GET, httpEntity,
				DockerResult.class);

		return (DockerResult) tags.getBody();
	}

	private String getDockerTagsEndpointUrl() {
		return String.format(dockerValidatiorProperties.getDockerRegistryUrl() + DOCKER_REGISTRY_TAGS_PATH, getDockerImageWithoutVersion(dockerResource));
	}

	private String getDockerImageWithoutVersion(DockerResource dockerResource) {
		try {
			String uri = dockerResource.getURI().toString().substring("docker:".length());
			DockerImage dockerImage = DockerImage.fromImageName(uri);
			StringBuilder sb = new StringBuilder();
			if (StringUtils.hasText(dockerImage.getHost())) {
				sb.append(dockerImage.getHost());
				sb.append(DockerImage.SECTION_SEPARATOR);
			}
			sb.append(dockerImage.getNamespaceAndRepo());
			return sb.toString();
		}
		catch (IOException e) {
			throw new IllegalArgumentException(
					"Docker Resource URI is not in expected format to extract version. " +
							dockerResource.getDescription(), e);
		}
	}
}
