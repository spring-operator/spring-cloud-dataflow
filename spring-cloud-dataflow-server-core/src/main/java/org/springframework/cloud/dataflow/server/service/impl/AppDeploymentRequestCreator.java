/*
 * Copyright 2017 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.BindingPropertyKeys;
import org.springframework.cloud.dataflow.core.DataFlowPropertyKeys;
import org.springframework.cloud.dataflow.core.StreamAppDefinition;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.core.StreamPropertyKeys;
import org.springframework.cloud.dataflow.registry.AppRegistryCommon;
import org.springframework.cloud.dataflow.registry.domain.AppRegistration;
import org.springframework.cloud.dataflow.rest.util.DeploymentPropertiesUtils;
import org.springframework.cloud.dataflow.server.config.apps.CommonApplicationProperties;
import org.springframework.cloud.dataflow.server.controller.WhitelistProperties;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Create the list of {@link AppDeploymentRequest}s from a {@link StreamDefinition} and
 * deployment properties map.
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Patrick Peralta
 * @author Ilayaperumal Gopinathan
 * @author Marius Bogoevici
 * @author Janne Valkealahti
 */
public class AppDeploymentRequestCreator {

	private static final String DEFAULT_PARTITION_KEY_EXPRESSION = "payload";

	private static Log logger = LogFactory.getLog(AppDeploymentRequestCreator.class);

	private final AppRegistryCommon appRegistry;

	private final CommonApplicationProperties commonApplicationProperties;

	private final WhitelistProperties whitelistProperties;

	public AppDeploymentRequestCreator(AppRegistryCommon appRegistry,
			CommonApplicationProperties commonApplicationProperties,
			ApplicationConfigurationMetadataResolver metadataResolver) {
		Assert.notNull(appRegistry, "AppRegistry must not be null");
		Assert.notNull(commonApplicationProperties, "CommonApplicationProperties must not be null");
		Assert.notNull(metadataResolver, "MetadataResolver must not be null");
		this.appRegistry = appRegistry;
		this.commonApplicationProperties = commonApplicationProperties;
		this.whitelistProperties = new WhitelistProperties(metadataResolver);
	}

	public List<AppDeploymentRequest> createUpdateRequests(StreamDefinition streamDefinition,
			Map<String, String> updateProperties) {
		List<AppDeploymentRequest> appDeploymentRequests = new ArrayList<>();
		if (updateProperties == null) {
			updateProperties = Collections.emptyMap();
		}
		Iterator<StreamAppDefinition> iterator = streamDefinition.getDeploymentOrderIterator();
		while (iterator.hasNext()) {
			StreamAppDefinition currentApp = iterator.next();
			ApplicationType type = currentApp.getApplicationType();
			AppRegistration appRegistration = this.appRegistry.find(currentApp.getRegisteredAppName(), type);
			Assert.notNull(appRegistration,
					String.format("no application '%s' of type '%s' exists in the registry",
							currentApp.getName(), type));

			String version = extractAppVersionProperty(currentApp, updateProperties);
			List<String> commandlineArguments = new ArrayList<>();
			if (version != null) {
				commandlineArguments.add(version);
			}
			Map<String, String> appUpdateTimeProperties = extractAppProperties(currentApp, updateProperties);
			Map<String, String> deployerDeploymentProperties = DeploymentPropertiesUtils
					.extractAndQualifyDeployerProperties(updateProperties, currentApp.getName());

			Resource appResource = appRegistry.getAppResource(appRegistration);
			Resource metadataResource = appRegistry.getAppMetadataResource(appRegistration);
			Map<String, String> expandedAppUpdateTimeProperties = (appUpdateTimeProperties.isEmpty()) ? new HashMap<>():
				this.whitelistProperties.qualifyProperties(appUpdateTimeProperties, metadataResource);

			expandedAppUpdateTimeProperties.put(DataFlowPropertyKeys.STREAM_APP_TYPE, type.toString());
			AppDefinition appDefinition = new AppDefinition(currentApp.getName(), expandedAppUpdateTimeProperties);

			AppDeploymentRequest request = new AppDeploymentRequest(appDefinition, appResource,
					deployerDeploymentProperties, commandlineArguments);

			appDeploymentRequests.add(request);
		}
		return appDeploymentRequests;
	}

	private String extractAppVersionProperty(StreamAppDefinition appDefinition, Map<String, String> updateProperties) {
		String versionPrefix = String.format("version.%s", appDefinition.getName());
		for (Map.Entry<String, String> entry : updateProperties.entrySet()) {
			if (entry.getKey().startsWith(versionPrefix)) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * Create a list of {@link AppDeploymentRequest}s from the provided
	 * {@link StreamDefinition} and map of deployment properties.
	 * @param streamDefinition the stream definition
	 * @param streamDeploymentProperties the stream's deployment properties
	 * @return list of AppDeploymentRequests
	 */
	public List<AppDeploymentRequest> createRequests(StreamDefinition streamDefinition,
			Map<String, String> streamDeploymentProperties) {
		List<AppDeploymentRequest> appDeploymentRequests = new ArrayList<>();
		if (streamDeploymentProperties == null) {
			streamDeploymentProperties = Collections.emptyMap();
		}
		Iterator<StreamAppDefinition> iterator = streamDefinition.getDeploymentOrderIterator();
		int nextAppCount = 0;
		boolean isDownStreamAppPartitioned = false;
		while (iterator.hasNext()) {
			StreamAppDefinition currentApp = iterator.next();
			AppRegistration appRegistration = this.appRegistry.find(currentApp.getRegisteredAppName(), currentApp.getApplicationType());
			Assert.notNull(appRegistration, String.format("no application '%s' of type '%s' exists in the registry",
					currentApp.getName(), currentApp.getApplicationType()));

			Map<String, String> appDeployTimeProperties = extractAppProperties(currentApp, streamDeploymentProperties);
			Map<String, String> deployerDeploymentProperties = DeploymentPropertiesUtils
					.extractAndQualifyDeployerProperties(streamDeploymentProperties, currentApp.getName());
			deployerDeploymentProperties.put(AppDeployer.GROUP_PROPERTY_KEY, currentApp.getStreamName());

			String version = extractAppVersionProperty(currentApp, streamDeploymentProperties);
			List<String> commandlineArguments = new ArrayList<>();
			if (version != null) {
				// TODO ensure new version as a resource exists and load that AppRegistration
				commandlineArguments.add(version);
			}

			// Set instance count property
			if (deployerDeploymentProperties.containsKey(AppDeployer.COUNT_PROPERTY_KEY)) {
				appDeployTimeProperties.put(StreamPropertyKeys.INSTANCE_COUNT,
						deployerDeploymentProperties.get(AppDeployer.COUNT_PROPERTY_KEY));
			}

			boolean upstreamAppSupportsPartition = upstreamAppHasPartitionInfo(streamDefinition, currentApp,
					streamDeploymentProperties);

			if (currentApp.getApplicationType() != ApplicationType.app) {
				if (upstreamAppSupportsPartition) {
					deployerDeploymentProperties.put(AppDeployer.INDEXED_PROPERTY_KEY, "true");
					updateConsumerPartitionProperties(appDeployTimeProperties);
				}

				// producer app partition properties
				if (isDownStreamAppPartitioned) {
					updateProducerPartitionProperties(appDeployTimeProperties, nextAppCount);
				}
			}

			nextAppCount = getInstanceCount(deployerDeploymentProperties);

			if (currentApp.getApplicationType() != ApplicationType.app) {
				isDownStreamAppPartitioned = isPartitionedConsumer(appDeployTimeProperties, upstreamAppSupportsPartition);
			}

			logger.info(String.format("Creating resource with [%s] for application [%s]",
					appRegistration.getUri().toString(), currentApp.getName()));
			Resource appResource = this.appRegistry.getAppResource(appRegistration);
			Resource metadataResource = this.appRegistry.getAppMetadataResource(appRegistration);

			// add properties needed for metrics system

			// TODO removing adding these generated properties has other side effects....
			appDeployTimeProperties.put(DataFlowPropertyKeys.STREAM_NAME, currentApp.getStreamName());
			appDeployTimeProperties.put(DataFlowPropertyKeys.STREAM_APP_LABEL, currentApp.getName());
			appDeployTimeProperties.put(DataFlowPropertyKeys.STREAM_APP_TYPE, currentApp.getApplicationType().toString());
			StringBuilder sb = new StringBuilder().append(currentApp.getStreamName()).append(".")
					.append(currentApp.getName()).append(".").append("${spring.cloud.application.guid}");
			appDeployTimeProperties.put(StreamPropertyKeys.METRICS_KEY, sb.toString());

			// Merge *definition time* app properties with *deployment time* properties
			// and expand them to their long form if applicable
			AppDefinition revisedDefinition = mergeAndExpandAppProperties(currentApp, metadataResource,
					appDeployTimeProperties);

			AppDeploymentRequest request = new AppDeploymentRequest(revisedDefinition, appResource,
					deployerDeploymentProperties, commandlineArguments);

			logger.debug("Created AppDeploymentRequest = " + request.toString() + " AppDefinition = "
					+ request.getDefinition().toString());
			appDeploymentRequests.add(request);
		}
		return appDeploymentRequests;
	}

	/**
	 * Extract and return a map of properties for a specific app within the deployment
	 * properties of a stream.
	 *
	 * @param appDefinition the {@link StreamAppDefinition} for which to return a map of
	 * properties
	 * @param streamDeploymentProperties deployment properties for the stream that the app is
	 * defined in
	 * @return map of properties for an app
	 */
	/* default */ Map<String, String> extractAppProperties(StreamAppDefinition appDefinition,
			Map<String, String> streamDeploymentProperties) {
		Map<String, String> appDeploymentProperties = new HashMap<>();
		appDeploymentProperties.putAll(this.commonApplicationProperties.getStream());
		// add properties with wild card prefix
		String wildCardProducerPropertyPrefix = "app.*.producer.";
		String wildCardConsumerPropertyPrefix = "app.*.consumer.";
		String wildCardPrefix = "app.*.";
		parseAndPopulateProperties(streamDeploymentProperties, appDeploymentProperties, wildCardProducerPropertyPrefix,
				wildCardConsumerPropertyPrefix, wildCardPrefix);
		// add application specific properties
		String producerPropertyPrefix = String.format("app.%s.producer.", appDefinition.getName());
		String consumerPropertyPrefix = String.format("app.%s.consumer.", appDefinition.getName());
		String appPrefix = String.format("app.%s.", appDefinition.getName());
		parseAndPopulateProperties(streamDeploymentProperties, appDeploymentProperties, producerPropertyPrefix,
				consumerPropertyPrefix, appPrefix);
		return appDeploymentProperties;
	}

	/**
	 * Return {@code true} if the upstream app (the app that appears before the provided app)
	 * contains partition related properties.
	 *
	 * @param stream stream for the app
	 * @param currentApp app for which to determine if the upstream app has partition
	 * properties
	 * @param streamDeploymentProperties deployment properties for the stream
	 * @return true if the upstream app has partition properties
	 */
	/* default */ boolean upstreamAppHasPartitionInfo(StreamDefinition stream, StreamAppDefinition currentApp,
			Map<String, String> streamDeploymentProperties) {
		Iterator<StreamAppDefinition> iterator = stream.getDeploymentOrderIterator();
		while (iterator.hasNext()) {
			StreamAppDefinition app = iterator.next();
			if (app.equals(currentApp) && iterator.hasNext()) {
				StreamAppDefinition prevApp = iterator.next();
				Map<String, String> appDeploymentProperties = extractAppProperties(prevApp, streamDeploymentProperties);
				return appDeploymentProperties.containsKey(BindingPropertyKeys.OUTPUT_PARTITION_KEY_EXPRESSION)
						|| appDeploymentProperties
								.containsKey(BindingPropertyKeys.OUTPUT_PARTITION_KEY_EXTRACTOR_CLASS);
			}
		}
		return false;
	}

	/* default */ void parseAndPopulateProperties(Map<String, String> streamDeploymentProperties,
			Map<String, String> appDeploymentProperties, String producerPropertyPrefix,
			String consumerPropertyPrefix,
			String appPrefix) {
		for (Map.Entry<String, String> entry : streamDeploymentProperties.entrySet()) {
			if (entry.getKey().startsWith(appPrefix)) {
				if (entry.getKey().startsWith(producerPropertyPrefix)) {
					appDeploymentProperties.put(BindingPropertyKeys.OUTPUT_BINDING_KEY_PREFIX
							+ entry.getKey().substring(appPrefix.length()), entry.getValue());
				}
				else if (entry.getKey().startsWith(consumerPropertyPrefix)) {
					appDeploymentProperties.put(
							BindingPropertyKeys.INPUT_BINDING_KEY_PREFIX + entry.getKey().substring(appPrefix.length()),
							entry.getValue());
				}
				else {
					appDeploymentProperties.put(entry.getKey().substring(appPrefix.length()), entry.getValue());
				}
			}
		}
	}

	/**
	 * Return a new app definition where definition-time and deploy-time properties have been
	 * merged and short form parameters have been expanded to their long form (amongst the
	 * whitelisted supported properties of the app) if applicable.
	 */
	/* default */ AppDefinition mergeAndExpandAppProperties(StreamAppDefinition original, Resource metadataResource,
			Map<String, String> appDeployTimeProperties) {
		Map<String, String> merged = new HashMap<>(original.getProperties());
		merged.putAll(appDeployTimeProperties);
		merged = this.whitelistProperties.qualifyProperties(merged, metadataResource);

		merged.putIfAbsent(StreamPropertyKeys.METRICS_PROPERTIES, "spring.application.name,spring.application.index,"
				+ "spring.cloud.application.*,spring.cloud.dataflow.*");
		merged.putIfAbsent(StreamPropertyKeys.METRICS_TRIGGER_INCLUDES, "integration**");

		return new AppDefinition(original.getName(), merged);
	}

	/**
	 * Add app properties for producing partitioned data to the provided properties.
	 *
	 * @param properties properties to update
	 * @param nextInstanceCount the number of instances for the next (downstream) app in the
	 * stream
	 */
	/* default */ void updateProducerPartitionProperties(Map<String, String> properties, int nextInstanceCount) {
		properties.put(BindingPropertyKeys.OUTPUT_PARTITION_COUNT, String.valueOf(nextInstanceCount));
		if (!properties.containsKey(BindingPropertyKeys.OUTPUT_PARTITION_KEY_EXPRESSION)) {
			properties.put(BindingPropertyKeys.OUTPUT_PARTITION_KEY_EXPRESSION, DEFAULT_PARTITION_KEY_EXPRESSION);
		}
	}

	/**
	 * Add app properties for consuming partitioned data to the provided properties.
	 *
	 * @param properties properties to update
	 */
	/* default */ void updateConsumerPartitionProperties(Map<String, String> properties) {
		properties.put(BindingPropertyKeys.INPUT_PARTITIONED, "true");
	}

	/**
	 * Return the app instance count indicated in the provided properties.
	 *
	 * @param properties deployer properties for the app for which to determine the count
	 * @return instance count indicated in the provided properties; if the properties do not
	 * contain a count, a value of {@code 1} is returned
	 */
	/* default */ int getInstanceCount(Map<String, String> properties) {
		return Integer.valueOf(properties.getOrDefault(AppDeployer.COUNT_PROPERTY_KEY, "1"));
	}

	/**
	 * Return {@code true} if an app is a consumer of partitioned data. This is determined
	 * either by the deployment properties for the app or whether the previous (upstream) app
	 * is publishing partitioned data.
	 *
	 * @param appDeploymentProperties deployment properties for the app
	 * @param upstreamAppSupportsPartition if true, previous (upstream) app in the stream
	 * publishes partitioned data
	 * @return true if the app consumes partitioned data
	 */
	/* default */ boolean isPartitionedConsumer(Map<String, String> appDeploymentProperties,
			boolean upstreamAppSupportsPartition) {
		return upstreamAppSupportsPartition
				|| (appDeploymentProperties.containsKey(BindingPropertyKeys.INPUT_PARTITIONED)
						&& appDeploymentProperties.get(BindingPropertyKeys.INPUT_PARTITIONED).equalsIgnoreCase("true"));
	}

}
