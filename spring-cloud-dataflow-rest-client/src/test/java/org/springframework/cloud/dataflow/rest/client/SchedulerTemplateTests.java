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

package org.springframework.cloud.dataflow.rest.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.springframework.cloud.dataflow.rest.resource.RootResource;
import org.springframework.cloud.dataflow.rest.resource.ScheduleInfoResource;
import org.springframework.cloud.dataflow.rest.util.DeploymentPropertiesUtils;
import org.springframework.hateoas.Link;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Glenn Renfro
 */
public class SchedulerTemplateTests {
	private static final String SCHEDULES_RELATION = org.springframework.cloud.dataflow.rest.client.SchedulerTemplate.SCHEDULES_RELATION;
	private static final String SCHEDULES_RELATION_INSTANCE = SCHEDULES_RELATION + "/instances";
	private static final String DEFAULT_SCHEDULE_NAME = "testSchedule";
	private static final String DEFAULT_DEFINITION_NAME = "testDefName";

	private RootResource rootResource;
	private RestTemplate restTemplate;
	private SchedulerTemplate template;

	@Before
	public void setup() {
		rootResource = mock(RootResource.class);
		when(rootResource.getLink(SCHEDULES_RELATION)).thenReturn(new Link(SCHEDULES_RELATION));
		when(rootResource.getLink(SCHEDULES_RELATION_INSTANCE)).thenReturn(new Link(SCHEDULES_RELATION_INSTANCE));
		restTemplate = mock(RestTemplate.class);
		template = new SchedulerTemplate(restTemplate, rootResource);
	}

	@Test
	public void scheduleTest() {
		Map<String, String> props = Collections.singletonMap("Hello", "World");
		List<String> args = Collections.singletonList("args=vals");
		template.schedule(DEFAULT_SCHEDULE_NAME, DEFAULT_DEFINITION_NAME, props, args);

		MultiValueMap<String, Object> values = new LinkedMultiValueMap<>();
		values.add("scheduleName", DEFAULT_SCHEDULE_NAME);
		values.add("properties", DeploymentPropertiesUtils.format(props));
		values.add("taskDefinitionName", DEFAULT_DEFINITION_NAME);
		values.add("arguments", args);
		Mockito.verify(restTemplate).postForObject(SCHEDULES_RELATION, values, Long.class);
	}

	@Test
	public void unScheduleTest() {
		template.unschedule(DEFAULT_SCHEDULE_NAME);
		Mockito.verify(restTemplate).delete(SCHEDULES_RELATION + "/testSchedule");
	}

	@Test
	public void listTest() {
		template.list();
		Mockito.verify(restTemplate).getForObject(SCHEDULES_RELATION, ScheduleInfoResource.Page.class);
	}

	@Test
	public void listTaskDefNameTest() {
		template.list(DEFAULT_DEFINITION_NAME);
		Mockito.verify(restTemplate).getForObject(SCHEDULES_RELATION_INSTANCE, ScheduleInfoResource.Page.class);
	}

	@Test
	public void getScheduleTest() {
		template.getSchedule(DEFAULT_SCHEDULE_NAME);
		Mockito.verify(restTemplate).getForObject(SCHEDULES_RELATION + "/" + DEFAULT_SCHEDULE_NAME,
				ScheduleInfoResource.class);
	}

}
