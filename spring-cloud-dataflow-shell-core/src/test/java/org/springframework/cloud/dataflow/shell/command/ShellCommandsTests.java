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

package org.springframework.cloud.dataflow.shell.command;

import java.io.FileNotFoundException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.registry.AppRegistry;
import org.springframework.cloud.dataflow.registry.domain.AppRegistration;
import org.springframework.cloud.dataflow.rest.client.config.DataFlowClientAutoConfiguration;
import org.springframework.cloud.dataflow.shell.AbstractShellIntegrationTest;
import org.springframework.cloud.dataflow.shell.EnableDataFlowShell;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for {@link org.springframework.cloud.dataflow.shell.ShellCommandLineRunner}.
 *
 * @author Furer Alexander
 */
public class ShellCommandsTests extends AbstractShellIntegrationTest {

	@After
	@Before
	public void unregisterAll() {
		AppRegistry registry = applicationContext.getBean(AppRegistry.class);
		for (AppRegistration appReg : registry.findAll()) {
			registry.delete(appReg.getName(), appReg.getType());
		}
	}

	@Test
	public void testSingleFileCommand() {

		String commandFiles = toAbsolutePaths("commands/registerTask_timestamp.txt");
		assertTrue(runShell(commandFiles));

		assertAppExists("timestamp", ApplicationType.task);

	}

	@Test
	public void testMultiFileCommandOrderPreserved() {
		String commandFiles = toAbsolutePaths(
				"commands/registerTask_timestamp.txt,commands/unregisterTask_timestamp.txt,commands/registerSink_log.txt,commands/unregisterSink_log.txt");
		assertTrue(runShell(commandFiles));

		assertThat("Registry should be empty", AbstractShellIntegrationTest.applicationContext.getBean(AppRegistry.class).findAll(),
				Matchers.empty());

	}

	@Test
	public void testMultiFileCommand() {
		String commandFiles = toAbsolutePaths("commands/registerTask_timestamp.txt,commands/registerSink_log.txt");
		assertTrue(runShell(commandFiles));

		assertAppExists("timestamp", ApplicationType.task);
		assertAppExists("log", ApplicationType.sink);
	}

	private void assertAppExists(String name, ApplicationType type) {
		AppRegistry registry = AbstractShellIntegrationTest.applicationContext.getBean(AppRegistry.class);
		assertTrue(String.format("'%s' application should be registered", name), registry.appExist(name, type));
	}

	/**
	 * Convert comma separated resources locations to comma separated absolute paths string
	 * @param fileResources
	 * @return
	 */
	private String toAbsolutePaths(String fileResources) {
		return Stream.of(fileResources.split(","))
				.map(f -> {
					try {
						return ResourceUtils.getFile("classpath:" + f).getAbsolutePath();
					}
					catch (FileNotFoundException e) {
						throw new RuntimeException(e);
					}
				})
				.collect(Collectors.joining(","));
	}

	/**
	 * Runs shell with given command files, waits 1 min for  completion
	 * @param commandFiles
	 */
	private boolean runShell(String commandFiles) {
		String dataFlowUri = applicationContext.getEnvironment().getProperty("dataflow.uri");
		ExecutorService executorService = Executors.newFixedThreadPool(1);
		Future<?> completed = executorService.submit(() -> {
			new SpringApplicationBuilder(ShellApp.class)
					.web(false)
					.bannerMode(Banner.Mode.OFF)
					.run(
							"--spring.shell.command-file=" + commandFiles,
							"--spring.cloud.config.enabled=false",
							"--spring.autoconfigure.exclude=" + Stream.of(SessionAutoConfiguration.class,
									DataSourceAutoConfiguration.class,
									HibernateJpaAutoConfiguration.class)
									.map(Class::getName)
									.collect(Collectors.joining(",")),
							"--dataflow.uri=" + dataFlowUri

					);
		});

		try {
			completed.get(60, TimeUnit.SECONDS);
			return true;
		}
		catch (Throwable e) {
			return false;
		}
		finally {
			executorService.shutdownNow();
		}

	}

	@EnableDataFlowShell
	@Configuration
	@EnableAutoConfiguration(exclude = DataFlowClientAutoConfiguration.class)
	public static class ShellApp {
	}

}
