/*
 * Copyright 2015-2018 the original author or authors.
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

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.EmbeddedDataSourceConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.TaskDefinition;
import org.springframework.cloud.dataflow.registry.AppRegistry;
import org.springframework.cloud.dataflow.registry.domain.AppRegistration;
import org.springframework.cloud.dataflow.server.DockerValidatorProperties;
import org.springframework.cloud.dataflow.server.audit.service.AuditRecordService;
import org.springframework.cloud.dataflow.server.config.apps.CommonApplicationProperties;
import org.springframework.cloud.dataflow.server.configuration.TaskServiceDependencies;
import org.springframework.cloud.dataflow.server.repository.DuplicateTaskException;
import org.springframework.cloud.dataflow.server.repository.InMemoryDeploymentIdRepository;
import org.springframework.cloud.dataflow.server.repository.NoSuchTaskDefinitionException;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.TaskService;
import org.springframework.cloud.dataflow.server.service.TaskValidationService;
import org.springframework.cloud.dataflow.server.service.ValidationStatus;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.cloud.task.repository.TaskRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 * @author David Turanski
 * @author Gunnar Hillert
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { EmbeddedDataSourceConfiguration.class, TaskServiceDependencies.class })
@EnableConfigurationProperties({ CommonApplicationProperties.class, TaskConfigurationProperties.class, DockerValidatorProperties.class})
public abstract class DefaultTaskServiceTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private final static String BASE_TASK_NAME = "myTask";

	private final static String TASK_NAME_ORIG = BASE_TASK_NAME + "_ORIG";

	@TestPropertySource(properties = { "spring.cloud.dataflow.task.maximum-concurrent-tasks=10" })
	public static class SimpleTaskTests extends DefaultTaskServiceTests {

		@Autowired
		TaskRepository taskExecutionRepository;

		@Autowired
		DataSourceProperties dataSourceProperties;

		@Autowired
		private TaskDefinitionRepository taskDefinitionRepository;

		@Autowired
		private TaskExplorer taskExplorer;

		@Autowired
		private AppRegistry appRegistry;

		@Autowired
		private TaskLauncher taskLauncher;

		@Autowired
		private ApplicationConfigurationMetadataResolver metadataResolver;

		@Autowired
		private TaskService taskService;

		@Autowired
		private CommonApplicationProperties commonApplicationProperties;

		@Autowired
		private TaskValidationService taskValidationService;

		@Autowired
		private AuditRecordService auditRecordService;

		@Before
		public void setupMockMVC() {
			taskDefinitionRepository.save(new TaskDefinition(TASK_NAME_ORIG, "demo"));
		}

		@Test
		@DirtiesContext
		public void createSimpleTask() {
			initializeSuccessfulRegistry(appRegistry);
			taskService.saveTaskDefinition("simpleTask", "AAA --foo=bar");
			verifyTaskExistsInRepo("simpleTask", "AAA --foo=bar", taskDefinitionRepository);
		}

		@Test
		@DirtiesContext
		public void executeSingleTaskTest() {
			initializeSuccessfulRegistry(appRegistry);
			when(taskLauncher.launch(anyObject())).thenReturn("0");
			assertEquals(1L, this.taskService.executeTask(TASK_NAME_ORIG, new HashMap<>(), new LinkedList<>()));
		}

		@Test
		@DirtiesContext
		public void executeMultipleTasksTest() {
			initializeSuccessfulRegistry(appRegistry);
			when(taskLauncher.launch(anyObject())).thenReturn("0");
			assertEquals(1L, this.taskService.executeTask(TASK_NAME_ORIG, new HashMap<>(), new LinkedList<>()));
			assertEquals(2L, this.taskService.executeTask(TASK_NAME_ORIG, new HashMap<>(), new LinkedList<>()));
		}

		@Test
		@DirtiesContext
		public void failOnLimitReached() {
			initializeSuccessfulRegistry(this.appRegistry);
			when(taskLauncher.launch(anyObject())).thenReturn("0");
			assertEquals(10, taskService.getMaximumConcurrentTasks());
			for (long i = 1; i <= taskService.getMaximumConcurrentTasks(); i++) {
				assertEquals(i, taskService.executeTask(TASK_NAME_ORIG, new HashMap<>(), new LinkedList<>()));
			}

			thrown.expect(IllegalStateException.class);
			thrown.expectMessage("The maximum concurrent task executions [10] is at its limit.");

			taskService.executeTask(TASK_NAME_ORIG, new HashMap<>(), new LinkedList<>());
		}

		@Test
		@DirtiesContext
		public void executeTaskWithNullIDReturnedTest() {
			initializeSuccessfulRegistry(appRegistry);
			boolean errorCaught = false;
			when(this.taskLauncher.launch(anyObject())).thenReturn(null);
			try {
				taskService.executeTask(TASK_NAME_ORIG, new HashMap<>(), new LinkedList<>());
			}
			catch (IllegalStateException ise) {
				errorCaught = true;
				assertEquals("Deployment ID is null for the task:myTask_ORIG", ise.getMessage());
			}
			if (!errorCaught) {
				fail();
			}
		}

		@Test
		@DirtiesContext
		public void executeTaskWithNullDefinitionTest() {
			boolean errorCaught = false;
			when(this.taskLauncher.launch(anyObject())).thenReturn("0");
			TaskService taskService = new DefaultTaskService(this.dataSourceProperties,
					mock(TaskDefinitionRepository.class), this.taskExplorer, this.taskExecutionRepository,
					this.appRegistry, this.taskLauncher, this.metadataResolver, new TaskConfigurationProperties(),
					new InMemoryDeploymentIdRepository(), auditRecordService, null, this.commonApplicationProperties,
					this.taskValidationService);
			try {
				taskService.executeTask(TASK_NAME_ORIG, new HashMap<>(), new LinkedList<>());
			}
			catch (NoSuchTaskDefinitionException ise) {
				errorCaught = true;
				assertEquals("Could not find task definition named myTask_ORIG", ise.getMessage());
			}
			if (!errorCaught) {
				fail();
			}
		}

		@Test
		@DirtiesContext
		public void validateValidTaskTest() {
			initializeSuccessfulRegistry(appRegistry);
			taskService.saveTaskDefinition("simpleTask", "AAA --foo=bar");
			ValidationStatus validationStatus = taskService.validateTask("simpleTask");
			assertEquals("valid", validationStatus.getAppsStatuses().get("task:simpleTask"));
		}

		@Test
		@DirtiesContext
		public void validateInvalidTaskTest() {
			initializeFailRegistry(appRegistry);
			taskService.saveTaskDefinition("simpleTask", "AAA --foo=bar");
			ValidationStatus validationStatus = taskService.validateTask("simpleTask");
			assertEquals("invalid", validationStatus.getAppsStatuses().get("task:simpleTask"));
		}
	}

	@TestPropertySource(properties = { "spring.cloud.dataflow.applicationProperties.task.globalkey=globalvalue",
		"spring.cloud.dataflow.applicationProperties.stream.globalstreamkey=nothere" })
	public static class ComposedTaskTests extends DefaultTaskServiceTests {
		@Autowired
		TaskRepository taskExecutionRepository;

		@Autowired
		DataSourceProperties dataSourceProperties;

		@Autowired
		private TaskDefinitionRepository taskDefinitionRepository;

		@Autowired
		private AppRegistry appRegistry;

		@Autowired
		private TaskLauncher taskLauncher;

		@Autowired
		private TaskService taskService;

		@Test
		@DirtiesContext
		public void executeComposedTask() {
			String dsl = "AAA && BBB";
			initializeSuccessfulRegistry(appRegistry);
			taskService.saveTaskDefinition("seqTask", dsl);
			when(taskLauncher.launch(anyObject())).thenReturn("0");
			Map<String, String> properties = new HashMap<>();
			properties.put("app.foo", "bar");
			properties.put("app.seqTask.AAA.timestamp.format", "YYYY");
			properties.put("deployer.seqTask.AAA.memory", "1240m");
			properties.put("app.composed-task-runner.interval-time-between-checks", "1000");
			assertEquals(1L, this.taskService.executeTask("seqTask", properties, new LinkedList<>()));
			ArgumentCaptor<AppDeploymentRequest> argumentCaptor = ArgumentCaptor.forClass(AppDeploymentRequest.class);
			verify(this.taskLauncher, atLeast(1)).launch(argumentCaptor.capture());

			AppDeploymentRequest request = argumentCaptor.getValue();
			assertEquals("seqTask", request.getDefinition().getProperties().get("spring.cloud.task.name"));
			assertTrue(request.getDefinition().getProperties().containsKey("composed-task-properties"));
			assertEquals(
				"app.seqTask-AAA.app.AAA.timestamp.format=YYYY, deployer.seqTask-AAA.deployer.AAA.memory=1240m",
				request.getDefinition().getProperties().get("composed-task-properties"));
			assertTrue(request.getDefinition().getProperties().containsKey("interval-time-between-checks"));
			assertEquals("1000", request.getDefinition().getProperties().get("interval-time-between-checks"));
			assertFalse(request.getDefinition().getProperties().containsKey("app.foo"));
			assertEquals("globalvalue", request.getDefinition().getProperties().get("globalkey"));
			assertNull(request.getDefinition().getProperties().get("globalstreamkey"));
		}

		@Test
		@DirtiesContext
		public void executeComposedTaskWithLabels() {
			String dsl = "t1: AAA && t2: BBB";
			initializeSuccessfulRegistry(appRegistry);
			taskService.saveTaskDefinition("seqTask", dsl);
			when(taskLauncher.launch(anyObject())).thenReturn("0");
			Map<String, String> properties = new HashMap<>();
			properties.put("app.seqTask.t1.timestamp.format", "YYYY");
			properties.put("app.composed-task-runner.interval-time-between-checks", "1000");
			assertEquals(1L, this.taskService.executeTask("seqTask", properties, new LinkedList<>()));
			ArgumentCaptor<AppDeploymentRequest> argumentCaptor = ArgumentCaptor.forClass(AppDeploymentRequest.class);
			verify(this.taskLauncher, atLeast(1)).launch(argumentCaptor.capture());

			AppDeploymentRequest request = argumentCaptor.getValue();
			assertEquals("seqTask", request.getDefinition().getProperties().get("spring.cloud.task.name"));
			assertTrue(request.getDefinition().getProperties().containsKey("composed-task-properties"));
			assertEquals("app.seqTask-t1.app.AAA.timestamp.format=YYYY",
				request.getDefinition().getProperties().get("composed-task-properties"));
			assertTrue(request.getDefinition().getProperties().containsKey("interval-time-between-checks"));
			assertEquals("1000", request.getDefinition().getProperties().get("interval-time-between-checks"));
		}

		@Test
		@DirtiesContext
		public void createSequenceComposedTask() {
			initializeSuccessfulRegistry(appRegistry);
			String dsl = "AAA && BBB";
			taskService.saveTaskDefinition("seqTask", dsl);
			verifyTaskExistsInRepo("seqTask", dsl, taskDefinitionRepository);

			verifyTaskExistsInRepo("seqTask-AAA", "AAA", taskDefinitionRepository);
			verifyTaskExistsInRepo("seqTask-BBB", "BBB", taskDefinitionRepository);
		}

		@Test
		@DirtiesContext
		public void createSplitComposedTask() {
			initializeSuccessfulRegistry(appRegistry);
			String dsl = "<AAA || BBB>";
			taskService.saveTaskDefinition("splitTask", dsl);
			verifyTaskExistsInRepo("splitTask", dsl, taskDefinitionRepository);

			verifyTaskExistsInRepo("splitTask-AAA", "AAA", taskDefinitionRepository);
			verifyTaskExistsInRepo("splitTask-BBB", "BBB", taskDefinitionRepository);
		}

		@Test
		@DirtiesContext
		public void verifyComposedTaskFlag() {
			String composedTaskDsl = "<AAA || BBB>";
			assertTrue("Expected true for composed task", taskService.isComposedDefinition(composedTaskDsl));
			composedTaskDsl = "AAA 'FAILED' -> BBB '*' -> CCC";
			assertTrue("Expected true for composed task", taskService.isComposedDefinition(composedTaskDsl));
			composedTaskDsl = "AAA && BBB && CCC";
			assertTrue("Expected true for composed task", taskService.isComposedDefinition(composedTaskDsl));
			String nonComposedTaskDsl = "AAA";
			assertFalse("Expected false for non-composed task", taskService.isComposedDefinition(nonComposedTaskDsl));
			nonComposedTaskDsl = "AAA --foo=bar";
			assertFalse("Expected false for non-composed task", taskService.isComposedDefinition(nonComposedTaskDsl));
		}

		@Test
		@DirtiesContext
		public void createTransitionComposedTask() {
			initializeSuccessfulRegistry(appRegistry);
			String dsl = "AAA 'FAILED' -> BBB '*' -> CCC";
			taskService.saveTaskDefinition("transitionTask", dsl);
			verifyTaskExistsInRepo("transitionTask", dsl, taskDefinitionRepository);

			verifyTaskExistsInRepo("transitionTask-AAA", "AAA", taskDefinitionRepository);
			verifyTaskExistsInRepo("transitionTask-BBB", "BBB", taskDefinitionRepository);
		}

		@Test
		@DirtiesContext
		public void deleteComposedTask() {
			initializeSuccessfulRegistry(appRegistry);
			String dsl = "AAA && BBB && CCC";
			taskService.saveTaskDefinition("deleteTask", dsl);
			verifyTaskExistsInRepo("deleteTask-AAA", "AAA", taskDefinitionRepository);
			verifyTaskExistsInRepo("deleteTask-BBB", "BBB", taskDefinitionRepository);
			verifyTaskExistsInRepo("deleteTask-CCC", "CCC", taskDefinitionRepository);
			verifyTaskExistsInRepo("deleteTask", dsl, taskDefinitionRepository);

			long preDeleteSize = taskDefinitionRepository.count();
			taskService.deleteTaskDefinition("deleteTask");
			assertThat(preDeleteSize - 4, is(equalTo(taskDefinitionRepository.count())));
		}

		@Test
		@DirtiesContext
		public void deleteComposedTaskMissingChildTasks() {
			initializeSuccessfulRegistry(appRegistry);
			String dsl = "AAA && BBB && CCC";
			taskService.saveTaskDefinition("deleteTask", dsl);
			verifyTaskExistsInRepo("deleteTask-AAA", "AAA", taskDefinitionRepository);
			verifyTaskExistsInRepo("deleteTask-BBB", "BBB", taskDefinitionRepository);
			verifyTaskExistsInRepo("deleteTask-CCC", "CCC", taskDefinitionRepository);
			verifyTaskExistsInRepo("deleteTask", dsl, taskDefinitionRepository);
			taskService.deleteTaskDefinition("deleteTask-BBB");
			long preDeleteSize = taskDefinitionRepository.count();
			taskService.deleteTaskDefinition("deleteTask");
			assertThat(preDeleteSize - 3, is(equalTo(taskDefinitionRepository.count())));
		}

		@Test
		@DirtiesContext
		public void deleteComposedTaskDeleteOnlyChildren() {
			initializeSuccessfulRegistry(appRegistry);
			taskService.saveTaskDefinition("deleteTask-AAA", "AAA");
			String dsl = "BBB && CCC";
			taskService.saveTaskDefinition("deleteTask", dsl);
			verifyTaskExistsInRepo("deleteTask-AAA", "AAA", taskDefinitionRepository);
			verifyTaskExistsInRepo("deleteTask-BBB", "BBB", taskDefinitionRepository);
			verifyTaskExistsInRepo("deleteTask-CCC", "CCC", taskDefinitionRepository);
			verifyTaskExistsInRepo("deleteTask", dsl, taskDefinitionRepository);

			long preDeleteSize = taskDefinitionRepository.count();
			taskService.deleteTaskDefinition("deleteTask");
			assertThat(preDeleteSize - 3, is(equalTo(taskDefinitionRepository.count())));
			verifyTaskExistsInRepo("deleteTask-AAA", "AAA", taskDefinitionRepository);
		}

		@Test
		@DirtiesContext
		public void deleteComposedTaskWithLabel() {
			initializeSuccessfulRegistry(appRegistry);
			String dsl = "LLL: AAA && BBB";
			taskService.saveTaskDefinition("deleteTask", dsl);
			verifyTaskExistsInRepo("deleteTask-LLL", "AAA", taskDefinitionRepository);
			verifyTaskExistsInRepo("deleteTask-BBB", "BBB", taskDefinitionRepository);
			verifyTaskExistsInRepo("deleteTask", dsl, taskDefinitionRepository);

			long preDeleteSize = taskDefinitionRepository.count();
			taskService.deleteTaskDefinition("deleteTask");
			assertThat(preDeleteSize - 3, is(equalTo(taskDefinitionRepository.count())));
		}

		@Test
		@DirtiesContext
		public void createFailedComposedTask() {
			String dsl = "AAA && BBB";
			initializeFailRegistry(appRegistry);
			boolean isExceptionThrown = false;
			try {
				taskService.saveTaskDefinition("splitTask", dsl);
			}
			catch (IllegalArgumentException iae) {
				isExceptionThrown = true;
			}
			assertTrue("IllegalArgumentException was expected to be thrown", isExceptionThrown);
			assertFalse(wasTaskDefinitionCreated("splitTask", taskDefinitionRepository));
			assertFalse(wasTaskDefinitionCreated("splitTask-AAA", taskDefinitionRepository));
			assertFalse(wasTaskDefinitionCreated("splitTask-BBB", taskDefinitionRepository));
		}

		@Test
		@DirtiesContext
		public void createDuplicateComposedTask() {
			String dsl = "AAA && BBB";
			initializeSuccessfulRegistry(appRegistry);
			boolean isExceptionThrown = false;
			taskService.saveTaskDefinition("splitTask", dsl);
			try {
				taskService.saveTaskDefinition("splitTask", dsl);
			}
			catch (DuplicateTaskException de) {
				isExceptionThrown = true;
			}
			assertTrue("DuplicateTaskException was expected to be thrown", isExceptionThrown);
			assertTrue(wasTaskDefinitionCreated("splitTask", taskDefinitionRepository));
			assertTrue(wasTaskDefinitionCreated("splitTask-AAA", taskDefinitionRepository));
			assertTrue(wasTaskDefinitionCreated("splitTask-BBB", taskDefinitionRepository));
		}

		@Test
		@DirtiesContext
		public void createDuplicateChildTaskComposedTask() {
			String dsl = "AAA && BBB";
			initializeSuccessfulRegistry(appRegistry);
			boolean isExceptionThrown = false;
			taskService.saveTaskDefinition("splitTask-BBB", "BBB");
			try {
				taskService.saveTaskDefinition("splitTask", dsl);
			}
			catch (DuplicateTaskException de) {
				isExceptionThrown = true;
			}
			assertTrue("DuplicateTaskException was expected to be thrown", isExceptionThrown);
			assertFalse(wasTaskDefinitionCreated("splitTask", taskDefinitionRepository));
			assertFalse(wasTaskDefinitionCreated("splitTask-AAA", taskDefinitionRepository));
			assertTrue(wasTaskDefinitionCreated("splitTask-BBB", taskDefinitionRepository));
		}
	}

	private static void initializeSuccessfulRegistry(AppRegistry appRegistry) {
		when(appRegistry.find(anyString(), any(ApplicationType.class))).thenReturn(
			new AppRegistration("some-name", ApplicationType.task, URI.create("http://helloworld")));
		when(appRegistry.getAppResource(any())).thenReturn(new FileSystemResource("src/test/resources/apps/foo-task"));
		when(appRegistry.getAppMetadataResource(any())).thenReturn(null);
	}

	private static void initializeFailRegistry(AppRegistry appRegistry) throws IllegalArgumentException {
		when(appRegistry.find("BBB", ApplicationType.task)).thenThrow(new IllegalArgumentException(
			String.format("Application name '%s' with type '%s' does not exist in the app registry.", "fake",
				ApplicationType.task)));
		when(appRegistry.find("AAA", ApplicationType.task)).thenReturn(mock(AppRegistration.class));
	}

	private static void verifyTaskExistsInRepo(String taskName, String dsl,
		TaskDefinitionRepository taskDefinitionRepository) {
		TaskDefinition taskDefinition = taskDefinitionRepository.findOne(taskName);
		assertThat(taskDefinition.getName(), is(equalTo(taskName)));
		assertThat(taskDefinition.getDslText(), is(equalTo(dsl)));
	}

	private static boolean wasTaskDefinitionCreated(String taskName,
		TaskDefinitionRepository taskDefinitionRepository) {
		TaskDefinition taskDefinition = taskDefinitionRepository.findOne(taskName);
		return taskDefinition != null;
	}
}
