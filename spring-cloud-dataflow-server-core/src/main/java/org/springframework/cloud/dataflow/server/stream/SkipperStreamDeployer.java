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
package org.springframework.cloud.dataflow.server.stream;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.DataFlowPropertyKeys;
import org.springframework.cloud.dataflow.core.StreamAppDefinition;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.core.StreamDeployment;
import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.rest.SkipperStream;
import org.springframework.cloud.dataflow.server.controller.NoSuchAppException;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.skipper.ReleaseNotFoundException;
import org.springframework.cloud.skipper.SkipperException;
import org.springframework.cloud.skipper.client.SkipperClient;
import org.springframework.cloud.skipper.domain.AboutResource;
import org.springframework.cloud.skipper.domain.ConfigValues;
import org.springframework.cloud.skipper.domain.Deployer;
import org.springframework.cloud.skipper.domain.Info;
import org.springframework.cloud.skipper.domain.InstallProperties;
import org.springframework.cloud.skipper.domain.InstallRequest;
import org.springframework.cloud.skipper.domain.Package;
import org.springframework.cloud.skipper.domain.PackageIdentifier;
import org.springframework.cloud.skipper.domain.PackageMetadata;
import org.springframework.cloud.skipper.domain.Release;
import org.springframework.cloud.skipper.domain.SpringCloudDeployerApplicationManifest;
import org.springframework.cloud.skipper.domain.SpringCloudDeployerApplicationManifestReader;
import org.springframework.cloud.skipper.domain.SpringCloudDeployerApplicationSpec;
import org.springframework.cloud.skipper.domain.Template;
import org.springframework.cloud.skipper.domain.UpgradeProperties;
import org.springframework.cloud.skipper.domain.UpgradeRequest;
import org.springframework.cloud.skipper.domain.UploadRequest;
import org.springframework.cloud.skipper.io.DefaultPackageWriter;
import org.springframework.cloud.skipper.io.PackageWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.Resources;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Delegates to Skipper to deploy the stream.
 *
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Soby Chacko
 * @author Glenn Renfro
 * @author Christian Tzolov
 */
public class SkipperStreamDeployer implements StreamDeployer {

	private static Log logger = LogFactory.getLog(SkipperStreamDeployer.class);

	private final SkipperClient skipperClient;

	private final StreamDefinitionRepository streamDefinitionRepository;

	private final AppRegistryService appRegistryService;

	private final ForkJoinPool forkJoinPool;

	public SkipperStreamDeployer(SkipperClient skipperClient, StreamDefinitionRepository streamDefinitionRepository,
			AppRegistryService appRegistryService, ForkJoinPool forkJoinPool) {
		Assert.notNull(skipperClient, "SkipperClient can not be null");
		Assert.notNull(streamDefinitionRepository, "StreamDefinitionRepository can not be null");
		Assert.notNull(appRegistryService, "StreamDefinitionRepository can not be null");
		Assert.notNull(forkJoinPool, "ForkJoinPool can not be null");
		this.skipperClient = skipperClient;
		this.streamDefinitionRepository = streamDefinitionRepository;
		this.appRegistryService = appRegistryService;
		this.forkJoinPool = forkJoinPool;
	}

	public static List<AppStatus> deserializeAppStatus(String platformStatus) {
		try {
			if (platformStatus != null) {
				ObjectMapper mapper = new ObjectMapper();
				mapper.addMixIn(AppStatus.class, AppStatusMixin.class);
				mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
				SimpleModule module = new SimpleModule("CustomModel", Version.unknownVersion());
				SimpleAbstractTypeResolver resolver = new SimpleAbstractTypeResolver();
				resolver.addMapping(AppInstanceStatus.class, AppInstanceStatusImpl.class);
				module.setAbstractTypes(resolver);
				mapper.registerModule(module);
				TypeReference<List<AppStatus>> typeRef = new TypeReference<List<AppStatus>>() {
				};
				return mapper.readValue(platformStatus, typeRef);
			}
			return new ArrayList<AppStatus>();
		}
		catch (Exception e) {
			logger.error("Could not parse Skipper Platform Status JSON [" + platformStatus + "]. " +
					"Exception message = " + e.getMessage());
			return new ArrayList<AppStatus>();
		} 
	}

	@Override
	public DeploymentState streamState(String streamName) {
		return getStreamDeploymentState(streamName);
	}

	@Override
	public Map<StreamDefinition, DeploymentState> streamsStates(List<StreamDefinition> streamDefinitions) {
		Map<StreamDefinition, DeploymentState> states = new HashMap<>();
		for (StreamDefinition streamDefinition : streamDefinitions) {
			DeploymentState streamDeploymentState = getStreamDeploymentState(streamDefinition.getName());
			if (streamDeploymentState != null) {
				states.put(streamDefinition, streamDeploymentState);
			}
		}
		return states;
	}

	private DeploymentState getStreamDeploymentState(String streamName) {
		DeploymentState state = null;
		try {
			Info info = this.skipperClient.status(streamName);
			if (info.getStatus().getPlatformStatus() == null) {
				return getDeploymentStateFromStatusInfo(info);
			}
			List<AppStatus> appStatusList = deserializeAppStatus(info.getStatus().getPlatformStatus());
			Set<DeploymentState> deploymentStateList = appStatusList.stream().map(appStatus -> appStatus.getState())
					.collect(Collectors.toSet());
			DeploymentState aggregateState = StreamDeployerUtil.aggregateState(deploymentStateList);
			state = aggregateState;
		}
		catch (ReleaseNotFoundException e) {
			// a defined stream but unknown to skipper is considered to be in an undeployed state
			if (streamDefinitionExists(streamName)) {
				state = DeploymentState.undeployed;
			}
		}
		return state;
	}

	private DeploymentState getDeploymentStateFromStatusInfo(Info info) {
		switch (info.getStatus().getStatusCode()) {
		case FAILED:
			return DeploymentState.failed;
		case DELETED:
			return DeploymentState.undeployed;
		case UNKNOWN:
			return DeploymentState.unknown;
		case DEPLOYED:
			return DeploymentState.deployed;
		}
		return DeploymentState.unknown;
	}

	private boolean streamDefinitionExists(String streamName) {
		return this.streamDefinitionRepository.findOne(streamName) != null;
	}

	public Release deployStream(StreamDeploymentRequest streamDeploymentRequest) {
		validateAllAppsRegistered(streamDeploymentRequest);
		Map<String, String> streamDeployerProperties = streamDeploymentRequest.getStreamDeployerProperties();
		String packageVersion = streamDeployerProperties.get(SkipperStream.SKIPPER_PACKAGE_VERSION);
		Assert.isTrue(StringUtils.hasText(packageVersion), "Package Version must be set");
		logger.info("Deploying Stream " + streamDeploymentRequest.getStreamName() + " using skipper.");
		String repoName = streamDeployerProperties.get(SkipperStream.SKIPPER_REPO_NAME);
		repoName = (StringUtils.hasText(repoName)) ? (repoName) : "local";
		String platformName = streamDeployerProperties.get(SkipperStream.SKIPPER_PLATFORM_NAME);
		platformName = determinePlatformName(platformName);
		String packageName = streamDeployerProperties.get(SkipperStream.SKIPPER_PACKAGE_NAME);
		packageName = (StringUtils.hasText(packageName)) ? packageName : streamDeploymentRequest.getStreamName();
		// Create the package .zip file to upload
		File packageFile = createPackageForStream(packageName, packageVersion, streamDeploymentRequest);
		// Upload the package
		UploadRequest uploadRequest = new UploadRequest();
		uploadRequest.setName(packageName);
		uploadRequest.setVersion(packageVersion);
		uploadRequest.setExtension("zip");
		uploadRequest.setRepoName(repoName); // TODO use from skipperDeploymentProperties if set.
		try {
			uploadRequest.setPackageFileAsBytes(Files.readAllBytes(packageFile.toPath()));
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Can't read packageFile " + packageFile, e);
		}
		skipperClient.upload(uploadRequest);
		// Install the package
		String streamName = streamDeploymentRequest.getStreamName();
		InstallRequest installRequest = new InstallRequest();
		PackageIdentifier packageIdentifier = new PackageIdentifier();
		packageIdentifier.setPackageName(packageName);
		packageIdentifier.setPackageVersion(packageVersion);
		packageIdentifier.setRepositoryName(repoName);
		installRequest.setPackageIdentifier(packageIdentifier);
		InstallProperties installProperties = new InstallProperties();
		installProperties.setPlatformName(platformName);
		installProperties.setReleaseName(streamName);
		installProperties.setConfigValues(new ConfigValues());
		installRequest.setInstallProperties(installProperties);
		Release release = null;
		try {
			release = this.skipperClient.install(installRequest);
		}
		catch (Exception e) {
			logger.error("Skipper install failed. Deleting the package: " + packageName);
			try {
				this.skipperClient.packageDelete(packageName);
			}
			catch (Exception e1) {
				logger.error("Package delete threw exception: " + e1.getMessage());
			}
			throw new SkipperException(e.getMessage());
		}
		// TODO store releasename in deploymentIdRepository...
		return release;
	}

	private String determinePlatformName(final String platformName) {
		Resources<Deployer> deployerResources = skipperClient.listDeployers();
		Collection<Deployer> deployers = deployerResources.getContent();
		if (StringUtils.hasText(platformName)) {
			List<Deployer> filteredDeployers = deployers.stream()
					.filter(d -> d.getName().equals(platformName))
					.collect(Collectors.toList());
			if (filteredDeployers.size() == 0) {
				throw new IllegalArgumentException("No platform named '" + platformName + "'");
			}
			else {
				return platformName;
			}
		}
		else {
			if (deployers.size() == 0) {
				throw new IllegalArgumentException("No platforms configured");
			}
			else {
				String platformNameToUse = deployers.stream().findFirst().get().getName();
				logger.info("Using platform '" + platformNameToUse + "'");
				return platformNameToUse;
			}
		}
	}

	private void validateAllAppsRegistered(StreamDeploymentRequest streamDeploymentRequest) {
		StreamDefinition streamDefinition = this.streamDefinitionRepository.findOne(streamDeploymentRequest.getStreamName());
		for (AppDeploymentRequest adr : streamDeploymentRequest.getAppDeploymentRequests()) {
			String version = this.appRegistryService.getResourceVersion(adr.getResource());
			validateAppVersionIsRegistered(getRegisteredName(streamDefinition, adr.getDefinition().getName()), adr, version);
		}
	}

	private String getRegisteredName(StreamDefinition streamDefinition, String adrAppName) {
		for (StreamAppDefinition appDefinition: streamDefinition.getAppDefinitions()) {
			if (appDefinition.getName().equals(adrAppName)) {
				return appDefinition.getRegisteredAppName();
			}
		}
		return adrAppName;
	}

	public void validateAppVersionIsRegistered(StreamDefinition streamDefinition, AppDeploymentRequest appDeploymentRequest, String appVersion) {
		String registeredAppName = getRegisteredName(streamDefinition, appDeploymentRequest.getDefinition().getName());
		this.validateAppVersionIsRegistered(registeredAppName, appDeploymentRequest, appVersion);
	}

	private void validateAppVersionIsRegistered(String registeredAppName, AppDeploymentRequest appDeploymentRequest, String appVersion) {
		String appTypeString = appDeploymentRequest.getDefinition().getProperties()
				.get(DataFlowPropertyKeys.STREAM_APP_TYPE);
		ApplicationType applicationType = ApplicationType.valueOf(appTypeString);
		if (!this.appRegistryService.appExist(registeredAppName, applicationType, appVersion)) {
			throw new IllegalStateException(String.format("The %s:%s:%s app is not registered!",
					registeredAppName, appTypeString, appVersion));
		}
	}

	private File createPackageForStream(String packageName, String packageVersion,
			StreamDeploymentRequest streamDeploymentRequest) {
		PackageWriter packageWriter = new DefaultPackageWriter();
		Package pkgtoWrite = createPackage(packageName, packageVersion, streamDeploymentRequest);
		Path tempPath;
		try {
			tempPath = Files.createTempDirectory("streampackages");
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Can't create temp diroectory");
		}
		File outputDirectory = tempPath.toFile();

		File zipFile = packageWriter.write(pkgtoWrite, outputDirectory);
		return zipFile;
	}

	private Package createPackage(String packageName, String packageVersion,
			StreamDeploymentRequest streamDeploymentRequest) {
		Package pkg = new Package();
		PackageMetadata packageMetadata = new PackageMetadata();
		packageMetadata.setApiVersion(SkipperStream.SKIPPER_DEFAULT_API_VERSION);
		packageMetadata.setKind(SkipperStream.SKIPPER_DEFAULT_KIND);
		packageMetadata.setName(packageName);
		packageMetadata.setVersion(packageVersion);
		packageMetadata.setMaintainer(SkipperStream.SKIPPER_DEFAULT_MAINTAINER);
		packageMetadata.setDescription(streamDeploymentRequest.getDslText());
		pkg.setMetadata(packageMetadata);
		pkg.setDependencies(createDependentPackages(packageVersion, streamDeploymentRequest));
		return pkg;
	}

	private List<Package> createDependentPackages(String packageVersion,
			StreamDeploymentRequest streamDeploymentRequest) {
		List<Package> packageList = new ArrayList<>();
		for (AppDeploymentRequest appDeploymentRequest : streamDeploymentRequest.getAppDeploymentRequests()) {
			packageList.add(createDependentPackage(packageVersion, appDeploymentRequest));
		}
		return packageList;
	}

	private Package createDependentPackage(String packageVersion, AppDeploymentRequest appDeploymentRequest) {
		Package pkg = new Package();
		String packageName = appDeploymentRequest.getDefinition().getName();

		PackageMetadata packageMetadata = new PackageMetadata();
		packageMetadata.setApiVersion(SkipperStream.SKIPPER_DEFAULT_API_VERSION);
		packageMetadata.setKind(SkipperStream.SKIPPER_DEFAULT_KIND);
		packageMetadata.setName(packageName);
		packageMetadata.setVersion(packageVersion);
		packageMetadata.setMaintainer(SkipperStream.SKIPPER_DEFAULT_MAINTAINER);

		pkg.setMetadata(packageMetadata);

		ConfigValues configValues = new ConfigValues();
		Map<String, Object> configValueMap = new HashMap<>();
		Map<String, Object> metadataMap = new HashMap<>();
		Map<String, Object> specMap = new HashMap<>();

		// Add metadata
		metadataMap.put("name", packageName);

		// Add spec
		String resourceWithoutVersion = this.appRegistryService.getResourceWithoutVersion(appDeploymentRequest.getResource());
		specMap.put("resource", resourceWithoutVersion);
		specMap.put("applicationProperties", appDeploymentRequest.getDefinition().getProperties());
		specMap.put("deploymentProperties", appDeploymentRequest.getDeploymentProperties());
		String version = this.appRegistryService.getResourceVersion(appDeploymentRequest.getResource());
		// Add version, including possible override via deploymentProperties - hack to store version in cmdline args
		if (appDeploymentRequest.getCommandlineArguments().size() == 1) {
			specMap.put("version", appDeploymentRequest.getCommandlineArguments().get(0));
		}
		else {
			specMap.put("version", version);
		}
		// Add metadata and spec to top level map
		configValueMap.put("metadata", metadataMap);
		configValueMap.put("spec", specMap);

		DumperOptions dumperOptions = new DumperOptions();
		dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		dumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);
		dumperOptions.setPrettyFlow(false);
		dumperOptions.setSplitLines(false);
		Yaml yaml = new Yaml(dumperOptions);
		configValues.setRaw(yaml.dump(configValueMap));

		pkg.setConfigValues(configValues);
		pkg.setTemplates(createGenericTemplate());
		return pkg;

	}

	private List<Template> createGenericTemplate() {
		Template template = this.skipperClient.getSpringCloudDeployerApplicationTemplate();
		List<Template> templateList = new ArrayList<>();
		templateList.add(template);
		return templateList;
	}

	public void undeployStream(String streamName) {
		Resources<PackageMetadata> packageMetadataResources = this.skipperClient.search(streamName, false);
		if (!packageMetadataResources.getContent().isEmpty()) {
			try {
				this.skipperClient.delete(streamName, true);
			}
			catch (ReleaseNotFoundException e) {
				logger.info(String.format("Release not found for %s. Deleting the package %s", streamName, streamName));
				this.skipperClient.packageDelete(streamName);
			}
		}
	}

	@Override
	public Page<AppStatus> getAppStatuses(Pageable pageable) throws ExecutionException, InterruptedException {
		List<String> skipperStreams = new ArrayList<>();
		Iterable<StreamDefinition> streamDefinitions = this.streamDefinitionRepository.findAll();
		for (StreamDefinition streamDefinition : streamDefinitions) {
			skipperStreams.add(streamDefinition.getName());
		}

		List<AppStatus> allStatuses = getStreamStatuses(pageable, skipperStreams);

		List<AppStatus> pagedStatuses = allStatuses.stream().skip(pageable.getPageNumber() * pageable.getPageSize())
				.limit(pageable.getPageSize()).parallel().collect(Collectors.toList());

		return new PageImpl<>(pagedStatuses, pageable, allStatuses.size());
	}

	@Override
	public AppStatus getAppStatus(String appDeploymentId) {
		Iterable<StreamDefinition> streamDefinitions = this.streamDefinitionRepository.findAll();
		for (StreamDefinition streamDefinition : streamDefinitions) {
			List<AppStatus> appStatuses = skipperStatus(streamDefinition.getName());
			for (AppStatus appStatus : appStatuses) {
				if (appStatus.getDeploymentId().equals(appDeploymentId)) {
					return appStatus;
				}
			}
		}
		throw new NoSuchAppException(appDeploymentId);
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		AboutResource skipperInfo = skipperClient.info();
		Resources<Deployer> deployers = skipperClient.listDeployers();
		RuntimeEnvironmentInfo.Builder builder = new RuntimeEnvironmentInfo.Builder()
				.implementationName(skipperInfo.getVersionInfo().getServer().getName())
				.implementationVersion(skipperInfo.getVersionInfo().getServer().getVersion())
				.platformApiVersion("")
				.platformClientVersion("")
				.platformHostVersion("")
				.platformType("Skipper Managed")
				.spiClass(SkipperClient.class);
		for (Deployer d : deployers) {
			builder.addPlatformSpecificInfo(d.getName(), d.getType());
		}
		return builder.build();
	}

	@Override
	public StreamDeployment getStreamInfo(String streamName) {
		try {
			String manifest = this.manifest(streamName);
			List<SpringCloudDeployerApplicationManifest> appManifests =
					new SpringCloudDeployerApplicationManifestReader().read(manifest);
			Map<String, Map<String, String>> streamPropertiesMap = new HashMap<>();
			for (SpringCloudDeployerApplicationManifest applicationManifest : appManifests) {
				Map<String, String> versionAndDeploymentProperties = new HashMap<>();
				SpringCloudDeployerApplicationSpec spec = applicationManifest.getSpec();
				String applicationName = applicationManifest.getApplicationName();
				versionAndDeploymentProperties.putAll(spec.getDeploymentProperties());
				versionAndDeploymentProperties.put(SkipperStream.SKIPPER_SPEC_RESOURCE, spec.getResource());
				versionAndDeploymentProperties.put(SkipperStream.SKIPPER_SPEC_VERSION, spec.getVersion());
				streamPropertiesMap.put(applicationName, versionAndDeploymentProperties);
			}
			return new StreamDeployment(streamName, new JSONObject(streamPropertiesMap).toString());
		}
		catch (ReleaseNotFoundException e) {
			return new StreamDeployment(streamName);
		}
	}

	private List<AppStatus> getStreamStatuses(Pageable pageable, List<String> skipperStreamNames)
			throws ExecutionException, InterruptedException {
		return this.forkJoinPool.submit(() -> skipperStreamNames.stream().parallel()
				.map(this::skipperStatus).flatMap(List::stream).collect(Collectors.toList())).get();
	}

	private List<AppStatus> skipperStatus(String streamName) {
		List<AppStatus> appStatuses = new ArrayList<>();
		try {
			Info info = this.skipperClient.status(streamName);
			appStatuses.addAll(SkipperStreamDeployer.deserializeAppStatus(info.getStatus().getPlatformStatus()));
		}
		catch (Exception e) {
			// ignore as we query status for all the streams.
		}
		return appStatuses;
	}

	/**
	 * Update the stream identified by the PackageIdentifier and runtime configuration values.
	 * @param streamName the name of the stream to upgrade
	 * @param packageIdentifier the name of the package in skipper
	 * @param configYml the YML formatted configuration values to use when upgrading
	 * @param force the flag to indicate if the stream update is forced even if there are no differences from the existing stream
	 * @param appNames the app names to update
	 * @return release the upgraded release
	 */
	public Release upgradeStream(String streamName, PackageIdentifier packageIdentifier, String configYml,
			boolean force, List<String> appNames) {
		UpgradeRequest upgradeRequest = new UpgradeRequest();
		upgradeRequest.setPackageIdentifier(packageIdentifier);
		UpgradeProperties upgradeProperties = new UpgradeProperties();
		ConfigValues configValues = new ConfigValues();
		configValues.setRaw(configYml);
		upgradeProperties.setConfigValues(configValues);
		upgradeProperties.setReleaseName(streamName);
		upgradeRequest.setUpgradeProperties(upgradeProperties);
		upgradeRequest.setForce(force);
		upgradeRequest.setAppNames(appNames);
		return this.skipperClient.upgrade(upgradeRequest);
	}

	/**
	 * Rollback the stream to a specific version
	 * @param streamName the name of the stream to rollback
	 * @param releaseVersion the version of the stream to rollback to
	 */
	public void rollbackStream(String streamName, int releaseVersion) {
		this.skipperClient.rollback(streamName, releaseVersion);
	}

	public String manifest(String name, int version) {
		if (version > 0) {
			return this.skipperClient.manifest(name, version);
		}
		else {
			return this.skipperClient.manifest(name);
		}
	}

	public String manifest(String name) {
		return this.skipperClient.manifest(name);
	}

	public Collection<Release> history(String releaseName) {
		return this.skipperClient.history(releaseName).getContent();
	}

	public Collection<Deployer> platformList() {
		return this.skipperClient.listDeployers().getContent();
	}
}
