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

package org.springframework.cloud.dataflow.server.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.TaskDefinition;
import org.springframework.cloud.dataflow.core.dsl.TaskNode;
import org.springframework.cloud.dataflow.core.dsl.TaskParser;
import org.springframework.cloud.dataflow.registry.AppRegistryCommon;
import org.springframework.cloud.dataflow.registry.domain.AppRegistration;
import org.springframework.cloud.dataflow.rest.util.DeploymentPropertiesUtils;
import org.springframework.cloud.dataflow.server.audit.domain.AuditActionType;
import org.springframework.cloud.dataflow.server.audit.domain.AuditOperationType;
import org.springframework.cloud.dataflow.server.audit.service.AuditRecordService;
import org.springframework.cloud.dataflow.server.audit.service.AuditServiceUtils;
import org.springframework.cloud.dataflow.server.config.apps.CommonApplicationProperties;
import org.springframework.cloud.dataflow.server.controller.WhitelistProperties;
import org.springframework.cloud.dataflow.server.repository.NoSuchTaskDefinitionException;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.SchedulerService;
import org.springframework.cloud.dataflow.server.service.SchedulerServiceProperties;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.scheduler.spi.core.ScheduleInfo;
import org.springframework.cloud.scheduler.spi.core.ScheduleRequest;
import org.springframework.cloud.scheduler.spi.core.Scheduler;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Pageable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the {@link SchedulerService} interface. Provide service methods
 * for Scheduling tasks.
 *
 * @author Glenn Renfro
 * @author Chris Schaefer
 */
public class DefaultSchedulerService implements SchedulerService {

	private CommonApplicationProperties commonApplicationProperties;
	private Scheduler scheduler;
	private TaskDefinitionRepository taskDefinitionRepository;
	private AppRegistryCommon registry;
	private final TaskConfigurationProperties taskConfigurationProperties;
	private final DataSourceProperties dataSourceProperties;
	private final String dataflowServerUri;
	private final WhitelistProperties whitelistProperties;
	private final SchedulerServiceProperties schedulerServiceProperties;
	private final AuditRecordService auditRecordService;
	private final AuditServiceUtils auditServiceUtils;

	public DefaultSchedulerService(CommonApplicationProperties commonApplicationProperties,
			Scheduler scheduler, TaskDefinitionRepository taskDefinitionRepository,
			AppRegistryCommon registry, ResourceLoader resourceLoader,
			TaskConfigurationProperties taskConfigurationProperties,
			DataSourceProperties dataSourceProperties, String dataflowServerUri,
			ApplicationConfigurationMetadataResolver metaDataResolver,
			SchedulerServiceProperties schedulerServiceProperties,
			AuditRecordService auditRecordService) {
		Assert.notNull(commonApplicationProperties, "commonApplicationProperties must not be null");
		Assert.notNull(scheduler, "scheduler must not be null");
		Assert.notNull(registry, "UriRegistry must not be null");
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		Assert.notNull(taskDefinitionRepository, "TaskDefinitionRepository must not be null");
		Assert.notNull(taskConfigurationProperties, "taskConfigurationProperties must not be null");
		Assert.notNull(dataSourceProperties, "DataSourceProperties must not be null");
		Assert.notNull(metaDataResolver, "metaDataResolver must not be null");
		Assert.notNull(schedulerServiceProperties, "schedulerServiceProperties must not be null");
		Assert.notNull(auditRecordService, "AuditRecordService must not be null");

		this.dataSourceProperties = dataSourceProperties;
		this.commonApplicationProperties = commonApplicationProperties;
		this.scheduler = scheduler;
		this.taskDefinitionRepository = taskDefinitionRepository;
		this.registry = registry;
		this.taskConfigurationProperties = taskConfigurationProperties;
		this.dataflowServerUri = dataflowServerUri;
		this.whitelistProperties = new WhitelistProperties(metaDataResolver);
		this.schedulerServiceProperties = schedulerServiceProperties;
		this.auditRecordService = auditRecordService;
		this.auditServiceUtils = new AuditServiceUtils();
	}

	@Override
	public void schedule(String scheduleName, String taskDefinitionName, Map<String, String> taskDeploymentProperties,
			List<String> commandLineArgs) {
		Assert.hasText(taskDefinitionName, "The provided taskName must not be null or empty.");
		Assert.notNull(taskDeploymentProperties, "The provided taskDeploymentProperties must not be null.");
		TaskDefinition taskDefinition = this.taskDefinitionRepository.findOne(taskDefinitionName);
		if (taskDefinition == null) {
			throw new NoSuchTaskDefinitionException(taskDefinitionName);
		}
		TaskParser taskParser = new TaskParser(taskDefinition.getName(), taskDefinition.getDslText(), true, true);
		TaskNode taskNode = taskParser.parse();
		// if composed task definition replace definition with one composed task
		// runner and executable graph.
		if (taskNode.isComposed()) {
			taskDefinition = new TaskDefinition(taskDefinition.getName(),
					TaskServiceUtils.createComposedTaskDefinition(
							taskNode.toExecutableDSL(), this.taskConfigurationProperties));
			taskDeploymentProperties = TaskServiceUtils.establishComposedTaskProperties(taskDeploymentProperties, taskNode);
		}

		AppRegistration appRegistration = this.registry.find(taskDefinition.getRegisteredAppName(),
				ApplicationType.task);
		Assert.notNull(appRegistration, "Unknown task app: " + taskDefinition.getRegisteredAppName());
		Resource metadataResource = this.registry.getAppMetadataResource(appRegistration);

		taskDefinition = TaskServiceUtils.updateTaskProperties(taskDefinition, this.dataSourceProperties);

		Map<String, String> appDeploymentProperties = new HashMap<>(commonApplicationProperties.getTask());
		appDeploymentProperties.putAll(
				TaskServiceUtils.extractAppProperties(taskDefinition.getRegisteredAppName(), taskDeploymentProperties));

		Map<String, String> deployerDeploymentProperties = DeploymentPropertiesUtils
				.extractAndQualifyDeployerProperties(taskDeploymentProperties, taskDefinition.getRegisteredAppName());
		if (StringUtils.hasText(this.dataflowServerUri) && taskNode.isComposed()) {
			TaskServiceUtils.updateDataFlowUriIfNeeded(this.dataflowServerUri, appDeploymentProperties, commandLineArgs);
		}
		AppDefinition revisedDefinition = TaskServiceUtils.mergeAndExpandAppProperties(taskDefinition, metadataResource,
				appDeploymentProperties, whitelistProperties);
		DeploymentPropertiesUtils.validateDeploymentProperties(taskDeploymentProperties);
		taskDeploymentProperties = extractAndQualifySchedulerProperties(taskDeploymentProperties);
		ScheduleRequest scheduleRequest = new ScheduleRequest(revisedDefinition, taskDeploymentProperties,
				deployerDeploymentProperties, commandLineArgs, scheduleName, getTaskResource(taskDefinitionName));
		this.scheduler.schedule(scheduleRequest);
		this.auditRecordService.populateAndSaveAuditRecordUsingMapData(AuditOperationType.SCHEDULE, AuditActionType.CREATE,
			scheduleRequest.getScheduleName(), this.auditServiceUtils.convertScheduleRequestToAuditData(scheduleRequest));
	}

	@Override
	public void unschedule(String scheduleName) {
		final ScheduleInfo scheduleInfo = getSchedule(scheduleName);
		if (scheduleInfo != null) {
			this.scheduler.unschedule(scheduleInfo.getScheduleName());
			this.auditRecordService.populateAndSaveAuditRecord(
					AuditOperationType.SCHEDULE,
					AuditActionType.DELETE, scheduleInfo.getScheduleName(),
					scheduleInfo.getTaskDefinitionName());
		}
	}

	@Override
	public List<ScheduleInfo> list(Pageable pageable, String taskDefinitionName) {
		throw new UnsupportedOperationException("method not supported");
	}

	@Override
	public List<ScheduleInfo> list(Pageable pageable) {
		throw new UnsupportedOperationException("method not supported");
	}

	@Override
	public List<ScheduleInfo> list(String taskDefinitionName) {
		return limitScheduleInfoResultSize(scheduler.list(taskDefinitionName),
				this.schedulerServiceProperties.getMaxSchedulesReturned());
	}

	@Override
	public List<ScheduleInfo> list() {
		return limitScheduleInfoResultSize(scheduler.list(),
				this.schedulerServiceProperties.getMaxSchedulesReturned());
	}

	@Override
	public ScheduleInfo getSchedule(String scheduleName) {
		List<ScheduleInfo> result = list().stream()
				.filter(scheduleInfo -> scheduleInfo.getScheduleName().equals(scheduleName))
				.collect(Collectors.toList());
		Assert.isTrue(!(result.size() > 1), "more than one schedule was returned for scheduleName, should only be one");
		return result.size() > 0 ? result.get(0) : null;
	}

	private List<ScheduleInfo> limitScheduleInfoResultSize(List<ScheduleInfo> resultSet,
			int schedulerLimitResultSize) {
		if(resultSet.size() > schedulerLimitResultSize) {
			resultSet = resultSet.subList(0, schedulerLimitResultSize);
		}
		return resultSet;
	}

	/**
	 * Retain only properties that are meant for the <em>scheduler</em> of a given task(those
	 * that start with {@code scheduler.}and qualify all
	 * property values with the {@code spring.cloud.scheduler.} prefix.
	 *
	 * @param input the scheduler properties
	 * @return scheduler properties for the task
	 */
	private static Map<String, String> extractAndQualifySchedulerProperties(Map<String, String> input) {
		final String prefix = "scheduler.";
		final int prefixLength = prefix.length();

		Map<String, String> result = new TreeMap<>(input).entrySet().stream()
				.filter(kv -> kv.getKey().startsWith(prefix))
				.collect(Collectors.toMap(kv -> "spring.cloud.scheduler." + kv.getKey().substring(prefixLength), kv -> kv.getValue(),
						(fromWildcard, fromApp) -> fromApp));

		return result;
	}

	protected Resource getTaskResource(String taskDefinitionName) {
		TaskDefinition taskDefinition = this.taskDefinitionRepository.findOne(taskDefinitionName);
		if (taskDefinition == null) {
			throw new NoSuchTaskDefinitionException(taskDefinitionName);
		}
		AppRegistration appRegistration = null;
		if (TaskServiceUtils.isComposedTaskDefinition(taskDefinition.getDslText())) {
			appRegistration = this.registry.find(taskConfigurationProperties.getComposedTaskRunnerName(),
					ApplicationType.task);
		}
		else {
			appRegistration = this.registry.find(taskDefinition.getRegisteredAppName(),
					ApplicationType.task);
		}
		Assert.notNull(appRegistration, "Unknown task app: " + taskDefinition.getRegisteredAppName());
		return this.registry.getAppResource(appRegistration);
	}

}
