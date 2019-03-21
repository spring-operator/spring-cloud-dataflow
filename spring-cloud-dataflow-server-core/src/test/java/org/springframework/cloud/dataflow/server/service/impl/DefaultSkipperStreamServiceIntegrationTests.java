/*
 * Copyright 2017-2018 the original author or authors.
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
package org.springframework.cloud.dataflow.server.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.core.StreamDeployment;
import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.rest.SkipperStream;
import org.springframework.cloud.dataflow.rest.UpdateStreamRequest;
import org.springframework.cloud.dataflow.server.config.features.FeaturesProperties;
import org.springframework.cloud.dataflow.server.configuration.TestDependencies;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.SkipperStreamService;
import org.springframework.cloud.dataflow.server.support.MockUtils;
import org.springframework.cloud.dataflow.server.support.SkipperPackageUtils;
import org.springframework.cloud.dataflow.server.support.TestResourceUtils;
import org.springframework.cloud.skipper.ReleaseNotFoundException;
import org.springframework.cloud.skipper.client.SkipperClient;
import org.springframework.cloud.skipper.domain.InstallRequest;
import org.springframework.cloud.skipper.domain.Manifest;
import org.springframework.cloud.skipper.domain.Package;
import org.springframework.cloud.skipper.domain.PackageIdentifier;
import org.springframework.cloud.skipper.domain.PackageMetadata;
import org.springframework.cloud.skipper.domain.Release;
import org.springframework.cloud.skipper.domain.UpgradeRequest;
import org.springframework.cloud.skipper.domain.UploadRequest;
import org.springframework.hateoas.Resources;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Christian Tzolov
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestDependencies.class)
@TestPropertySource(properties = { "spring.main.banner-mode=off",
		FeaturesProperties.FEATURES_PREFIX + "." + FeaturesProperties.SKIPPER_ENABLED + "=true" })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DefaultSkipperStreamServiceIntegrationTests {

	@Autowired
	private SkipperStreamService streamService;

	@Autowired
	private StreamDefinitionRepository streamDefinitionRepository;

	@Autowired
	private AppRegistryService appRegistryService;

	@MockBean
	private SkipperClient skipperClient;

	@Before
	public void before() throws URISyntaxException {
		createTickTock();
		this.skipperClient = MockUtils.configureMock(this.skipperClient);
	}

	@After
	public void destroyStream() {
		when(this.skipperClient.search(anyString(), anyBoolean()))
				.thenReturn(new Resources<>(Collections.singletonList(new PackageMetadata())));
		streamService.undeployStream("ticktock");
		streamDefinitionRepository.deleteAll();
	}

	@Test
	public void validateSkipperDeploymentProperties() {

		Map<String, String> deploymentProperties = createSkipperDeploymentProperties();
		// override log version to 1.2.0.RELEASE
		deploymentProperties.put("badthing.version.log", "1.2.0.RELEASE");

		when(skipperClient.status(eq("ticktock"))).thenThrow(new ReleaseNotFoundException(""));
		try {
			streamService.deployStream("ticktock", deploymentProperties);
			fail("Expected an IllegalArgumentException to be thrown.");
		}
		catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("Only deployment property keys starting with 'app.' or 'deployer.'" +
					"  or 'version.' allowed, got 'badthing.version.log'");
		}
	}

	@Test
	public void testInstallVersionOverride() throws IOException {

		Map<String, String> deploymentProperties = createSkipperDeploymentProperties();
		// override log to 1.2.0.RELEASE
		deploymentProperties.put("version.log", "1.2.0.RELEASE");
		when(skipperClient.status(eq("ticktock"))).thenThrow(new ReleaseNotFoundException(""));

		streamService.deployStream("ticktock", deploymentProperties);

		ArgumentCaptor<UploadRequest> uploadRequestCaptor = ArgumentCaptor.forClass(UploadRequest.class);
		verify(skipperClient).upload(uploadRequestCaptor.capture());

		Package pkg = SkipperPackageUtils.loadPackageFromBytes(uploadRequestCaptor);

		// ExpectedYaml will have version: 1.2.0.RELEASE and not 1.1.1.RELEASE
		String expectedYaml = StreamUtils.copyToString(
				TestResourceUtils.qualifiedResource(getClass(), "install.yml").getInputStream(),
				Charset.defaultCharset());
		Package logPackage = null;
		for (Package subpkg : pkg.getDependencies()) {
			if (subpkg.getMetadata().getName().equals("log")) {
				logPackage = subpkg;
			}
		}
		assertThat(logPackage).isNotNull();
		String actualYaml = logPackage.getConfigValues().getRaw();

		DumperOptions dumperOptions = new DumperOptions();
		dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		dumperOptions.setPrettyFlow(true);
		Yaml yaml = new Yaml(dumperOptions);

		Object actualYamlLoaded = yaml.load(actualYaml);
		Object expectedYamlLoaded = yaml.load(expectedYaml);


		assertThat(actualYamlLoaded).isEqualTo(expectedYamlLoaded);
	}

	@Test
	public void testUpdateStreamDslOnDeploy() throws IOException {

		// Create stream
		StreamDefinition streamDefinition = new StreamDefinition("ticktock",
				"time --fixed-delay=100 | log --level=DEBUG");
		this.streamDefinitionRepository.delete(streamDefinition.getName());
		this.streamDefinitionRepository.save(streamDefinition);

		StreamDefinition streamDefinitionBeforeDeploy = this.streamDefinitionRepository.findOne("ticktock");
		assertThat(streamDefinitionBeforeDeploy.getDslText())
				.isEqualTo("time --fixed-delay=100 | log --level=DEBUG");

		String expectedReleaseManifest = StreamUtils.copyToString(
				TestResourceUtils.qualifiedResource(getClass(), "deployManifest.yml").getInputStream(),
				Charset.defaultCharset());
		Release release = new Release();
		Manifest manifest = new Manifest();
		manifest.setData(expectedReleaseManifest);
		release.setManifest(manifest);
		when(skipperClient.install(isA(InstallRequest.class))).thenReturn(release);
		when(skipperClient.status(eq("ticktock"))).thenThrow(new ReleaseNotFoundException(""));

		Map<String, String> deploymentProperties = createSkipperDeploymentProperties();
		deploymentProperties.put("version.log", "1.2.0.RELEASE");

		streamService.deployStream("ticktock", deploymentProperties);

		StreamDefinition streamDefinitionAfterDeploy = this.streamDefinitionRepository.findOne("ticktock");
		assertThat(streamDefinitionAfterDeploy.getDslText())
				.isEqualTo("time --trigger.fixed-delay=100 | log --log.level=DEBUG");
	}

	@Test
	public void testUpdateStreamDslOnUpgrade() throws IOException {

		// Create stream
		StreamDefinition streamDefinition = new StreamDefinition("ticktock",
				"time --fixed-delay=100 | log --level=DEBUG");
		this.streamDefinitionRepository.delete(streamDefinition.getName());
		this.streamDefinitionRepository.save(streamDefinition);

		when(skipperClient.status(eq("ticktock"))).thenThrow(new ReleaseNotFoundException(""));

		streamService.deployStream("ticktock", createSkipperDeploymentProperties());

		StreamDefinition streamDefinitionBeforeDeploy = this.streamDefinitionRepository.findOne("ticktock");
		assertThat(streamDefinitionBeforeDeploy.getDslText())
				.isEqualTo("time --fixed-delay=100 | log --level=DEBUG");

		String expectedReleaseManifest = StreamUtils.copyToString(
				TestResourceUtils.qualifiedResource(getClass(), "upgradeManifest.yml").getInputStream(),
				Charset.defaultCharset());
		Release release = new Release();
		Manifest manifest = new Manifest();
		manifest.setData(expectedReleaseManifest);
		release.setManifest(manifest);
		when(skipperClient.upgrade(isA(UpgradeRequest.class))).thenReturn(release);

		Map<String, String> deploymentProperties = createSkipperDeploymentProperties();
		deploymentProperties.put("version.log", "1.2.0.RELEASE");
		streamService.updateStream("ticktock",
				new UpdateStreamRequest("ticktock", new PackageIdentifier(), deploymentProperties));

		StreamDefinition streamDefinitionAfterDeploy = this.streamDefinitionRepository.findOne("ticktock");
		assertThat(streamDefinitionAfterDeploy.getDslText())
				.isEqualTo("time --trigger.fixed-delay=200 | log --log.level=INFO");
	}

	@Test
	public void testStreamInfo() throws IOException {

		// Create stream
		StreamDefinition streamDefinition = new StreamDefinition("ticktock",
				"time --fixed-delay=100 | log --level=DEBUG");
		this.streamDefinitionRepository.delete(streamDefinition.getName());
		this.streamDefinitionRepository.save(streamDefinition);

		when(skipperClient.status(eq("ticktock"))).thenThrow(new ReleaseNotFoundException(""));

		Map<String, String> deploymentProperties = createSkipperDeploymentProperties();
		this.streamService.deployStream("ticktock", deploymentProperties);
		String releaseManifest = StreamUtils.copyToString(
				TestResourceUtils.qualifiedResource(getClass(), "deployManifest.yml").getInputStream(),
				Charset.defaultCharset());
		String deploymentProps = StreamUtils.copyToString(
				TestResourceUtils.qualifiedResource(getClass(), "deploymentProps.json").getInputStream(),
				Charset.defaultCharset());
		when(skipperClient.manifest(streamDefinition.getName())).thenReturn(releaseManifest);
		StreamDeployment streamDeployment = this.streamService.info(streamDefinition.getName());
		assertThat(streamDeployment.getStreamName()).isEqualTo(streamDefinition.getName());
		assertThat(streamDeployment.getDeploymentProperties()).contains(deploymentProps);
	}

	private Map<String, String> createSkipperDeploymentProperties() {
		Map<String, String> deploymentProperties = new HashMap<>();
		deploymentProperties.put(SkipperStream.SKIPPER_PACKAGE_NAME, "ticktock");
		deploymentProperties.put(SkipperStream.SKIPPER_PACKAGE_VERSION, "1.0.0");
		return deploymentProperties;
	}

	private void createTickTock() throws URISyntaxException {
		String timeUri = "maven://org.springframework.cloud.stream.app:time-source-rabbit:1.2.0.RELEASE";
		appRegistryService.save("time", ApplicationType.source, "1.2.0.RELEASE", new URI(timeUri), null);
		String logUri = "maven://org.springframework.cloud.stream.app:log-sink-rabbit:1.1.1.RELEASE";
		appRegistryService.save("log", ApplicationType.sink, "1.2.0.RELEASE", new URI(logUri), null);

		// Create stream
		StreamDefinition streamDefinition = new StreamDefinition("ticktock", "time | log");
		this.streamDefinitionRepository.save(streamDefinition);
	}
}
