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

package org.springframework.cloud.dataflow.shell.command;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.dataflow.registry.AppRegistry;
import org.springframework.cloud.dataflow.shell.AbstractShellIntegrationTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.shell.core.CommandResult;
import org.springframework.shell.table.Table;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ilayaperumal Gopinathan
 * @author Mark Fisher
 * @author Glenn Renfro
 */
public class StreamCommandTests extends AbstractShellIntegrationTest {

	private static final String APPS_URI = "META-INF/test-stream-apps.properties";

	private static final Logger logger = LoggerFactory.getLogger(StreamCommandTests.class);

	@Before
	public void registerApps() {
		AppRegistry registry = applicationContext.getBean(AppRegistry.class);
		registry.importAll(true, new ClassPathResource(APPS_URI));
	}

	@Test
	public void testStreamLifecycleForTickTock() throws InterruptedException {
		logger.info("Starting Stream Test for TickTock");
		String streamName = generateUniqueName();
		stream().create(streamName, "time | log");
	}

	@Test
	public void testValidate() throws InterruptedException {
		String streamName = generateUniqueName();
		stream().create(streamName, "time | log");

		CommandResult cr = stream().validate(streamName);
		assertTrue("task validate status command must be successful", cr.isSuccess());
		List results = (List) cr.getResult();
		Table table = (Table)results.get(0);
		assertEquals("Number of columns returned was not expected", 2, table.getModel().getColumnCount());
		assertEquals("First Row First Value should be: Stream Name", "Stream Name", table.getModel().getValue(0, 0));
		assertEquals("First Row Second Value should be: Stream Definition", "Stream Definition", table.getModel().getValue(0, 1));
		assertEquals("Second Row First Value should be: " + streamName, streamName, table.getModel().getValue(1, 0));
		assertEquals("Second Row Second Value should be: time | log", "time | log", table.getModel().getValue(1, 1));

		String message = String.format("\n%s is a valid stream.", streamName);
		assertEquals(String.format("Notification should be: %s",message ), message, results.get(1));

		table = (Table)results.get(2);
		assertEquals("Number of columns returned was not expected", 2, table.getModel().getColumnCount());
		assertEquals("First Row First Value should be: App Name", "App Name", table.getModel().getValue(0, 0));
		assertEquals("First Row Second Value should be: Validation Status", "Validation Status", table.getModel().getValue(0, 1));
		assertEquals("Second Row First Value should be: source:time", "source:time" , table.getModel().getValue(1, 0));
		assertEquals("Second Row Second Value should be: valid", "valid", table.getModel().getValue(1, 1));
		assertEquals("Third Row First Value should be: sink:log", "sink:log" , table.getModel().getValue(2, 0));
		assertEquals("Third Row Second Value should be: valid", "valid", table.getModel().getValue(2, 1));

	}

}
