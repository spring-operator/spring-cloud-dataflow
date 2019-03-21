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

package org.springframework.cloud.dataflow.server.controller;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.server.configuration.TestDependencies;
import org.springframework.cloud.dataflow.server.support.LogTestNameRule;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Glenn Renfro
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestDependencies.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-implementation.version=1.2.3.IMPLEMENTATION.TEST",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-core.version=1.2.3.CORE.TEST",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-dashboard.version=1.2.3.UI.TEST",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.SHELL.TEST",
		"spring.cloud.dataflow.version-info.dependency-fetch.enabled=true",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.url=https://repo.spring.io/libs-milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.name=Spring Cloud Data Flow Shell Test",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1-url={repository}/org/springframework/cloud/spring-cloud-dataflow-shell/{version}/spring-cloud-dataflow-shell-{version}.jar.sha1"
})
public class AboutControllerTests {

	private MockMvc mockMvc;

	@Rule
	public LogTestNameRule logTestName = new LogTestNameRule();

	@Autowired
	private WebApplicationContext wac;

	@Before
	public void setupMocks() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
				.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
	}

	@Test
	public void testListApplications() throws Exception {
		ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
				result.andDo(print()).andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
				.andExpect(jsonPath("$.featureInfo.skipperEnabled", is(false)))
				.andExpect(jsonPath("$.versionInfo.implementation.name", is("${info.app.name}")))
				.andExpect(jsonPath("$.versionInfo.implementation.version", is("1.2.3.IMPLEMENTATION.TEST")))
				.andExpect(jsonPath("$.versionInfo.core.name", is("Spring Cloud Data Flow Core")))
				.andExpect(jsonPath("$.versionInfo.core.version", is("1.2.3.CORE.TEST")))
				.andExpect(jsonPath("$.versionInfo.dashboard.name", is("Spring Cloud Dataflow UI")))
				.andExpect(jsonPath("$.versionInfo.dashboard.version", is("1.2.3.UI.TEST")))
				.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell Test")))
				.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/libs-milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf")))
				.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.SHELL.TEST")))
				.andExpect(jsonPath("$.versionInfo.shell.checksumSha1", is("ABCDEFG")))
				.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist())
				.andExpect(jsonPath("$.securityInfo.authenticationEnabled", is(false)))
				.andExpect(jsonPath("$.securityInfo.authorizationEnabled", is(false)))
				.andExpect(jsonPath("$.securityInfo.formLogin", is(false)))
				.andExpect(jsonPath("$.securityInfo.authenticated", is(false)))
				.andExpect(jsonPath("$.securityInfo.username", isEmptyOrNullString()))
				.andExpect(jsonPath("$.featureInfo.streamsEnabled", is(true)))
				.andExpect(jsonPath("$.featureInfo.tasksEnabled", is(true)))
				.andExpect(jsonPath("$.featureInfo.skipperEnabled", is(false)))
				.andExpect(jsonPath("$.featureInfo.schedulerEnabled", is(false)))
				.andExpect(jsonPath("$.runtimeEnvironment.appDeployer.deployerName", not(isEmptyOrNullString())));
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.SHELL.TEST",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=false",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.url=https://repo.spring.io/libs-milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG"
	})
	public static class ChecksumDisabledTests {

		private MockMvc mockMvc;

		@Rule
		public LogTestNameRule logTestName = new LogTestNameRule();

		@Autowired
		private WebApplicationContext wac;

		@Before
		public void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		public void testChecksumDisabled() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/libs-milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.SHELL.TEST")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.M1",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=false",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG"
	})
	public static class MilestoneUrlTests {

		private MockMvc mockMvc;

		@Rule
		public LogTestNameRule logTestName = new LogTestNameRule();

		@Autowired
		private WebApplicationContext wac;

		@Before
		public void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		public void testMilestone() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/libs-milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.2.3.M1/spring-cloud-dataflow-shell-1.2.3.M1.jar")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.M1")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}


	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.RC1",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=false",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG"
	})
	public static class RCUrlTests {

		private MockMvc mockMvc;

		@Rule
		public LogTestNameRule logTestName = new LogTestNameRule();

		@Autowired
		private WebApplicationContext wac;

		@Before
		public void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		public void testRC() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/libs-milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.2.3.RC1/spring-cloud-dataflow-shell-1.2.3.RC1.jar")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.RC1")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.GA",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=false",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG"
	})
	public static class GAUrlTests {

		private MockMvc mockMvc;

		@Autowired
		private WebApplicationContext wac;

		@Before
		public void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		public void testGA() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo1.maven.org/maven2/org/springframework/cloud/spring-cloud-dataflow-shell/1.2.3.GA/spring-cloud-dataflow-shell-1.2.3.GA.jar")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.GA")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.RELEASE",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=false",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG"
	})
	public static class ReleaseUrlTests {

		private MockMvc mockMvc;

		@Rule
		public LogTestNameRule logTestName = new LogTestNameRule();

		@Autowired
		private WebApplicationContext wac;

		@Before
		public void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		public void testRelease() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/libs-release/org/springframework/cloud/spring-cloud-dataflow-shell/1.2.3.RELEASE/spring-cloud-dataflow-shell-1.2.3.RELEASE.jar")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.RELEASE")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.RELEASE",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=true",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.url=https://repo.spring.io/libs-milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1-url=",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha256-url="
	})
	public static class ChecksumNoDefaultTests {

		private MockMvc mockMvc;

		@Rule
		public LogTestNameRule logTestName = new LogTestNameRule();

		@Autowired
		private WebApplicationContext wac;

		@Before
		public void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		public void testChecksumNoDefaults() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/libs-milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.features.skipper-enabled=true",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-implementation.version=1.2.3.IMPLEMENTATION.TEST",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-core.version=1.2.3.CORE.TEST",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-dashboard.version=1.2.3.UI.TEST",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.SHELL.TEST",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=true",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.url=https://repo.spring.io/libs-milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.name=Spring Cloud Data Flow Shell Test",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1-url={repository}/org/springframework/cloud/spring-cloud-dataflow-shell/{version}/spring-cloud-dataflow-shell-{version}.jar.sha1"
	})
	public static class SkipperModeTests {

		private MockMvc mockMvc;

		@Rule
		public LogTestNameRule logTestName = new LogTestNameRule();

		@Autowired
		private WebApplicationContext wac;

		@Before
		public void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		public void testAboutInSkipperMode() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.featureInfo.skipperEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.implementation.name", is("${info.app.name}")))
					.andExpect(jsonPath("$.versionInfo.implementation.version", is("1.2.3.IMPLEMENTATION.TEST")))
					.andExpect(jsonPath("$.versionInfo.core.name", is("Spring Cloud Data Flow Core")))
					.andExpect(jsonPath("$.versionInfo.core.version", is("1.2.3.CORE.TEST")))
					.andExpect(jsonPath("$.versionInfo.dashboard.name", is("Spring Cloud Dataflow UI")))
					.andExpect(jsonPath("$.versionInfo.dashboard.version", is("1.2.3.UI.TEST")))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell Test")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/libs-milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.SHELL.TEST")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1", is("ABCDEFG")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist())
					.andExpect(jsonPath("$.securityInfo.authenticationEnabled", is(false)))
					.andExpect(jsonPath("$.securityInfo.authorizationEnabled", is(false)))
					.andExpect(jsonPath("$.securityInfo.formLogin", is(false)))
					.andExpect(jsonPath("$.securityInfo.authenticated", is(false)))
					.andExpect(jsonPath("$.securityInfo.username", isEmptyOrNullString()))
					.andExpect(jsonPath("$.runtimeEnvironment.appDeployer.deployerName", is("skipper server")));
		}
	}
}
