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

package org.springframework.cloud.dataflow.server.controller;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.core.TaskDefinition;
import org.springframework.cloud.dataflow.server.audit.domain.AuditActionType;
import org.springframework.cloud.dataflow.server.audit.domain.AuditOperationType;
import org.springframework.cloud.dataflow.server.audit.domain.AuditRecord;
import org.springframework.cloud.dataflow.server.audit.repository.AuditRecordRepository;
import org.springframework.cloud.dataflow.server.configuration.TestDependencies;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.SchedulerService;
import org.springframework.cloud.deployer.resource.registry.UriRegistry;
import org.springframework.cloud.scheduler.spi.core.ScheduleInfo;
import org.springframework.cloud.scheduler.spi.core.SchedulerPropertyKeys;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Tests for the {@link TaskSchedulerController}.
 *
 * @author Glenn Renfro
 * @author Christian Tzolov
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestDependencies.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class TaskSchedulerControllerTests {

	@Autowired
	SchedulerService schedulerService;

	@Autowired
	private WebApplicationContext wac;

	@Autowired
	private TaskDefinitionRepository repository;

	@Autowired
	private UriRegistry registry;

	@Autowired
	private TestDependencies.SimpleTestScheduler simpleTestScheduler;

	@Autowired
	private AuditRecordRepository auditRecordRepository;

	private MockMvc mockMvc;

	@Before
	public void setupMockMVC() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
				.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testTaskSchedulerControllerConstructorMissingService() {
		new TaskSchedulerController(null);
	}

	@Test
	public void testListSchedules() throws Exception {
		this.registry.register("task.testApp", new URI("file:src/test/resources/apps/foo-task"));

		repository.save(new TaskDefinition("testDefinition", "testApp"));
		createSampleSchedule("schedule1");
		createSampleSchedule("schedule2");
		mockMvc.perform(get("/tasks/schedules").accept(MediaType.APPLICATION_JSON)).
				andDo(print()).andExpect(status().isOk()).
				andExpect(jsonPath("$.content[*].scheduleName", containsInAnyOrder("schedule1","schedule2")))
				.andExpect(jsonPath("$.content", hasSize(2)));
	}

	@Test
	public void testGetSchedule() throws Exception {
		this.registry.register("task.testApp", new URI("file:src/test/resources/apps/foo-task"));

		repository.save(new TaskDefinition("testDefinition", "testApp"));
		createSampleSchedule("schedule1");
		createSampleSchedule("schedule2");
		mockMvc.perform(get("/tasks/schedules/schedule1").accept(MediaType.APPLICATION_JSON))
				.andDo(print()).andExpect(status().isOk())
				.andExpect(content().json("{scheduleName: \"schedule1\"}"))
				.andExpect(content().json("{taskDefinitionName: \"testDefinition\"}"));
		mockMvc.perform(get("/tasks/schedules/schedule2").accept(MediaType.APPLICATION_JSON))
				.andDo(print()).andExpect(status().isOk())
				.andExpect(content().json("{scheduleName: \"schedule2\"}"))
				.andExpect(content().json("{taskDefinitionName: \"testDefinition\"}"));
		mockMvc.perform(get("/tasks/schedules/scheduleNotExisting").accept(MediaType.APPLICATION_JSON))
				.andDo(print()).andExpect(status().isNotFound())
				.andExpect(content().json("[{\"logref\":\"NoSuchScheduleException\"," +
						"\"message\":\"Schedule [scheduleNotExisting] doesn't exist\",\"links\":[]}]"));
	}

	@Test
	public void testListSchedulesByTaskDefinitionName() throws Exception {
		this.registry.register("task.testApp", new URI("file:src/test/resources/apps/foo-task"));

		repository.save(new TaskDefinition("foo", "testApp"));
		repository.save(new TaskDefinition("bar", "testApp"));

		createSampleSchedule("foo", "schedule1");
		createSampleSchedule("bar", "schedule2");
		mockMvc.perform(get("/tasks/schedules/instances/bar").accept(MediaType.APPLICATION_JSON)).
				andDo(print()).andExpect(status().isOk()).
				andExpect(jsonPath("$.content[*].scheduleName", containsInAnyOrder("schedule2")))
				.andExpect(jsonPath("$.content", hasSize(1)));
	}

	@Test
	public void testCreateSchedule() throws Exception {
		this.registry.register("task.testApp", new URI("file:src/test/resources/apps/foo-task"));
		repository.save(new TaskDefinition("testDefinition", "testApp"));
		mockMvc.perform(post("/tasks/schedules/").param("taskDefinitionName", "testDefinition").
				param("scheduleName", "mySchedule").
				param("properties", "scheduler.cron.expression=* * * * *")
				.accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isCreated());
		assertEquals(1, simpleTestScheduler.list().size());
		ScheduleInfo scheduleInfo = simpleTestScheduler.list().get(0);
		assertEquals("mySchedule", scheduleInfo.getScheduleName());
		assertEquals(1, scheduleInfo.getScheduleProperties().size());
		assertEquals("* * * * *", scheduleInfo.getScheduleProperties().get("spring.cloud.scheduler.cron.expression"));

		final List<AuditRecord> auditRecords = auditRecordRepository.findAll();

		assertEquals(1, auditRecords.size());
		final AuditRecord auditRecord = auditRecords.get(0);

		assertEquals(AuditOperationType.SCHEDULE, auditRecord.getAuditOperation());
		assertEquals(AuditActionType.CREATE, auditRecord.getAuditAction());
		assertEquals("mySchedule", auditRecord.getCorrelationId());

		assertEquals("{\"commandlineArguments\":[],\"taskDefinitionName\":\"testDefinition\","
				+ "\"taskDefinitionProperties\":{\"spring.datasource.username\":null,\"spring.cloud.task.name\":\"testDefinition\",\"spring.datasource.url\":null,\"spring.datasource.driverClassName\":null},"
				+ "\"deploymentProperties\":{}}", auditRecord.getAuditData());

	}

	@Test
	public void testCreateScheduleWithSensitiveFields() throws Exception {
		this.registry.register("task.testApp", new URI("file:src/test/resources/apps/foo-task"));
		repository.save(new TaskDefinition("testDefinition", "testApp"));
		mockMvc.perform(post("/tasks/schedules/").param("taskDefinitionName", "testDefinition").
				param("scheduleName", "mySchedule").
				param("properties", "scheduler.cron.expression=* * * * *,app.testApp.prop1=foo,app.testApp.prop2.secret=kenny,deployer.*.prop1.secret=cartman,deployer.*.prop2.password=kyle").
				param("arguments", "argument1=foo,password=secret")
				.accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isCreated());
		assertEquals(1, simpleTestScheduler.list().size());
		ScheduleInfo scheduleInfo = simpleTestScheduler.list().get(0);
		assertEquals("mySchedule", scheduleInfo.getScheduleName());
		assertEquals(1, scheduleInfo.getScheduleProperties().size());
		assertEquals("* * * * *", scheduleInfo.getScheduleProperties().get("spring.cloud.scheduler.cron.expression"));

		final List<AuditRecord> auditRecords = auditRecordRepository.findAll();

		assertEquals(1, auditRecords.size());
		final AuditRecord auditRecord = auditRecords.get(0);

		assertEquals(AuditOperationType.SCHEDULE, auditRecord.getAuditOperation());
		assertEquals(AuditActionType.CREATE, auditRecord.getAuditAction());
		assertEquals("mySchedule", auditRecord.getCorrelationId());

		assertEquals("{\"commandlineArguments\":[\"argument1=foo\",\"password=******\"],\"taskDefinitionName\":\"testDefinition\","
				+ "\"taskDefinitionProperties\":{\"prop1\":\"foo\",\"spring.datasource.username\":null,\"prop2.secret\":\"******\",\"spring.datasource.url\":null,\"spring.datasource.driverClassName\":null,\"spring.cloud.task.name\":\"testDefinition\"},"
				+ "\"deploymentProperties\":{\"spring.cloud.deployer.prop1.secret\":\"******\",\"spring.cloud.deployer.prop2.password\":\"******\"}}", auditRecord.getAuditData());

	}

	@Test
	public void testCreateScheduleBadCron() throws Exception {
		this.registry.register("task.testApp", new URI("file:src/test/resources/apps/foo-task"));
		repository.save(new TaskDefinition("testDefinition", "testApp"));
		mockMvc.perform(post("/tasks/schedules/").param("taskDefinitionName", "testDefinition").
				param("scheduleName", "myScheduleBadCron").
				param("properties", "scheduler.cron.expression="+TestDependencies.SimpleTestScheduler.INVALID_CRON_EXPRESSION)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
	}

	@Test
	public void testRemoveSchedule() throws Exception {
		this.registry.register("task.testApp", new URI("file:src/test/resources/apps/foo-task"));
		repository.save(new TaskDefinition("testDefinition", "testApp"));
		createSampleSchedule("mySchedule");
		assertEquals(1, simpleTestScheduler.list().size());
		mockMvc.perform(delete("/tasks/schedules/"+"mySchedule" ).accept(MediaType.APPLICATION_JSON)).andDo(print())
				.andExpect(status().isOk());
		assertEquals(0, simpleTestScheduler.list().size());

		AuditActionType[] auditActionTypesCreate = {  AuditActionType.CREATE };
		final Page<AuditRecord> auditRecordsCreate = auditRecordRepository.findByAuditActionIn(auditActionTypesCreate, null);

		AuditActionType[] auditActionTypesDelete = {  AuditActionType.DELETE };
		final Page<AuditRecord> auditRecordsDelete = auditRecordRepository.findByAuditActionIn(auditActionTypesDelete, null);

		assertEquals(1, auditRecordsCreate.getContent().size());
		assertEquals(1, auditRecordsDelete.getContent().size());
		final AuditRecord auditRecord = auditRecordsDelete.getContent().get(0);

		assertEquals(AuditOperationType.SCHEDULE, auditRecord.getAuditOperation());
		assertEquals(AuditActionType.DELETE, auditRecord.getAuditAction());
		assertEquals("mySchedule", auditRecord.getCorrelationId());
		assertEquals("testDefinition", auditRecord.getAuditData());
	}

	private void createSampleSchedule(String scheduleName) {
		createSampleSchedule("testDefinition", scheduleName);
	}

	private void createSampleSchedule(String taskDefinitionName, String scheduleName) {
		Map<String, String> properties = new HashMap<>();
		properties.put("scheduler.testApp." + SchedulerPropertyKeys.CRON_EXPRESSION, "* * * * *");
		schedulerService.schedule(scheduleName, taskDefinitionName,properties, null);
	}

}
