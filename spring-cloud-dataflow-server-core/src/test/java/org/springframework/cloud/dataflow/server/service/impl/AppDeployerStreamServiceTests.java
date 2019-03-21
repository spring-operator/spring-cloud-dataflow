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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import org.springframework.cloud.dataflow.configuration.metadata.BootApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.core.StreamDeployment;
import org.springframework.cloud.dataflow.registry.AppRegistry;
import org.springframework.cloud.dataflow.registry.AppRegistryCommon;
import org.springframework.cloud.dataflow.server.DockerValidatorProperties;
import org.springframework.cloud.dataflow.server.audit.service.AuditRecordService;
import org.springframework.cloud.dataflow.server.config.apps.CommonApplicationProperties;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.cloud.dataflow.server.repository.StreamDeploymentRepository;
import org.springframework.cloud.dataflow.server.service.impl.validation.DefaultStreamValidationService;
import org.springframework.cloud.dataflow.server.stream.AppDeployerStreamDeployer;
import org.springframework.cloud.dataflow.server.stream.SkipperStreamDeployer;
import org.springframework.cloud.dataflow.server.stream.StreamDeployers;
import org.springframework.test.context.junit4.SpringRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Ilayaperumal Gopinathan
 * @author Eric Bottard
 * @author Christian Tzolov
 * @author Gunnar Hillert
 */
@RunWith(SpringRunner.class)
public class AppDeployerStreamServiceTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private StreamDefinition streamDefinition1 = new StreamDefinition("test1", "time | log");

	private StreamDefinition streamDefinition2 = new StreamDefinition("test2", "time | log");

	private StreamDefinition streamDefinition3 = new StreamDefinition("test3", "time | log");

	private StreamDeployment streamDeployment1 = new StreamDeployment(streamDefinition1.getName());

	private StreamDeployment streamDeployment2 = new StreamDeployment(streamDefinition2.getName());

	private StreamDeployment streamDeployment3 = new StreamDeployment(streamDefinition3.getName());

	private List<StreamDefinition> streamDefinitionList = new ArrayList<>();

	private List<StreamDefinition> appDeployerStreamDefinitions = new ArrayList<>();

	private List<StreamDefinition> skipperStreamDefinitions = new ArrayList<>();

	private StreamDeploymentRepository streamDeploymentRepository;

	private StreamDefinitionRepository streamDefinitionRepository;

	private AppDeployerStreamDeployer appDeployerStreamDeployer;

	private SkipperStreamDeployer skipperStreamDeployer;

	private AppDeploymentRequestCreator appDeploymentRequestCreator;

	private AppDeployerStreamService simpleStreamService;

	private AppRegistryCommon appRegistryCommon;

	private AuditRecordService auditRecordService;

	private DockerValidatorProperties dockerValidatorProperties;

	private DefaultStreamValidationService streamValidationService;

	@Before
	public void setupMock() {
		this.streamDeploymentRepository = mock(StreamDeploymentRepository.class);
		this.streamDefinitionRepository = mock(StreamDefinitionRepository.class);
		this.appDeployerStreamDeployer = mock(AppDeployerStreamDeployer.class);
		this.skipperStreamDeployer = mock(SkipperStreamDeployer.class);
		this.dockerValidatorProperties = new DockerValidatorProperties();
		this.appDeploymentRequestCreator = new AppDeploymentRequestCreator(mock(AppRegistry.class),
				mock(CommonApplicationProperties.class),
				new BootApplicationConfigurationMetadataResolver());
		this.appRegistryCommon = mock(AppRegistryCommon.class);
		this.auditRecordService = mock(AuditRecordService.class);
		this.streamValidationService = new DefaultStreamValidationService(this.appRegistryCommon,
				this.dockerValidatorProperties,
				this.streamDefinitionRepository);
		this.simpleStreamService = new AppDeployerStreamService(this.streamDefinitionRepository,
				this.appDeployerStreamDeployer, this.appDeploymentRequestCreator,
				this.streamValidationService, auditRecordService);
		this.streamDefinitionList.add(streamDefinition1);
		this.appDeployerStreamDefinitions.add(streamDefinition1);
		this.streamDefinitionList.add(streamDefinition2);
		this.streamDefinitionList.add(streamDefinition3);
		this.skipperStreamDefinitions.add(streamDefinition2);
		this.skipperStreamDefinitions.add(streamDefinition3);
		when(streamDefinitionRepository.findOne("test2")).thenReturn(streamDefinition2);
		when(streamDeploymentRepository.findOne(streamDeployment1.getStreamName())).thenReturn(streamDeployment1);
		when(streamDeploymentRepository.findOne(streamDeployment2.getStreamName())).thenReturn(streamDeployment2);
		when(streamDeploymentRepository.findOne(streamDeployment3.getStreamName())).thenReturn(streamDeployment3);
	}

	@Test
	public void verifyUndeployStream() {
		StreamDefinition streamDefinition1 = new StreamDefinition("test1", "time | log");
		when(streamDefinitionRepository.findOne("test1")).thenReturn(streamDefinition1);
		StreamDeployment streamDeployment1 = new StreamDeployment(streamDefinition1.getName(),
				StreamDeployers.appdeployer.name());
		when(this.streamDeploymentRepository.findOne(streamDeployment1.getStreamName())).thenReturn(streamDeployment1);
		this.simpleStreamService.undeployStream(streamDefinition1.getName());
		verify(this.appDeployerStreamDeployer, times(1)).undeployStream(streamDefinition1.getName());
		verify(this.streamDefinitionRepository, times(1)).findOne(streamDefinition1.getName());
		verifyNoMoreInteractions(this.appDeployerStreamDeployer);
		verifyNoMoreInteractions(this.streamDefinitionRepository);
		verify(this.skipperStreamDeployer, never()).undeployStream(streamDefinition1.getName());
	}

	@Test
	public void verifyStreamInfo() {
		StreamDefinition streamDefinition1 = new StreamDefinition("test1", "time | log");
		Map<String, String> deploymentProperties1 = new HashMap<>();
		deploymentProperties1.put("test1", "value1");
		Map<String, String> deploymentProperties2 = new HashMap<>();
		deploymentProperties2.put("test2", "value2");
		Map<String, Map<String, String>> streamDeploymentProperties = new HashMap<>();
		streamDeploymentProperties.put("time", deploymentProperties1);
		streamDeploymentProperties.put("log", deploymentProperties2);
		StreamDeployment streamDeployment1 = new StreamDeployment(streamDefinition1.getName(),
				new JSONObject(streamDeploymentProperties).toString());
		when(this.appDeployerStreamDeployer.getStreamInfo(streamDeployment1.getStreamName())).thenReturn(streamDeployment1);
		StreamDeployment streamDeployment = this.simpleStreamService.info("test1");
		Assert.assertTrue(streamDeployment.getStreamName().equals(streamDefinition1.getName()));
		Assert.assertTrue(streamDeployment.getDeploymentProperties().equals("{\"log\":{\"test2\":\"value2\"},\"time\":{\"test1\":\"value1\"}}"));
	}
}
