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
package org.springframework.cloud.dataflow.server.local.security;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import org.springframework.cloud.dataflow.server.local.LocalDataflowResource;
import org.springframework.data.authentication.UserCredentials;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.cloud.dataflow.server.local.security.SecurityTestUtils.basicAuthorizationHeader;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the root URL when security with file-based user list is enabled.
 * Task feature and Stream feature are disabled.
 *
 * @author Gunnar Hillert
 */
public class LocalServerSecurityRootWithUsersFileTestsWithoutTasksAndStreams {

	private final static LocalDataflowResource localDataflowResource = new LocalDataflowResource(
			"classpath:org/springframework/cloud/dataflow/server/local/security/fileBasedUsers.yml", false, false);

	@ClassRule
	public static TestRule springDataflowAndLdapServer = RuleChain.outerRule(localDataflowResource);

	private static UserCredentials viewOnlyUser = new UserCredentials("bob", "bobspassword");
	private static UserCredentials fullUser = new UserCredentials("fulluser", "fullpassword");

	@Test
	public void testAccessRootUrlAndCheckAllLinksWithFullUser() throws Exception {
		localDataflowResource.getMockMvc()
				.perform(get("/").header("Authorization", basicAuthorizationHeader(fullUser.getUsername(), fullUser.getPassword()))).andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$._links.*", hasSize(11)))
				.andExpect(jsonPath("$._links.dashboard.href", is("http://localhost/dashboard")))
				.andExpect(jsonPath("$._links.streams").doesNotExist())
				.andExpect(jsonPath("$._links.runtime").doesNotExist())
				.andExpect(jsonPath("$._links.metrics").doesNotExist())
				.andExpect(jsonPath("$._links.tasks").doesNotExist())
				.andExpect(jsonPath("$._links.jobs").doesNotExist())
				.andExpect(jsonPath("$._links.tools/parseTaskTextToGraph").doesNotExist())
				.andExpect(jsonPath("$._links.tools/convertTaskGraphToText").doesNotExist())
				.andExpect(jsonPath("$._links.counters.href", is("http://localhost/metrics/counters")))
				.andExpect(jsonPath("$._links.counters/counter.href", is("http://localhost/metrics/counters/{name}")))
				.andExpect(jsonPath("$._links.field-value-counters.href", is("http://localhost/metrics/field-value-counters")))
				.andExpect(jsonPath("$._links.field-value-counters/counter.href", is("http://localhost/metrics/field-value-counters/{name}")))
				.andExpect(jsonPath("$._links.aggregate-counters.href", is("http://localhost/metrics/aggregate-counters")))
				.andExpect(jsonPath("$._links.aggregate-counters/counter.href", is("http://localhost/metrics/aggregate-counters/{name}")))
				.andExpect(jsonPath("$._links.apps.href", is("http://localhost/apps")))
				.andExpect(jsonPath("$._links.about.href", is("http://localhost/about")))
				.andExpect(jsonPath("$._links.completions/stream.href", is("http://localhost/completions/stream{?start,detailLevel}")))
				.andExpect(jsonPath("$._links.completions/task.href", is("http://localhost/completions/task{?start,detailLevel}")));
	}

	@Test
	public void testAccessRootUrlAndCheckAllLinksWithViewOnlyUser() throws Exception {
		localDataflowResource.getMockMvc()
				.perform(get("/").header("Authorization", basicAuthorizationHeader(viewOnlyUser.getUsername(), viewOnlyUser.getPassword()))).andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$._links.*", hasSize(11)))
				.andExpect(jsonPath("$._links.dashboard.href", is("http://localhost/dashboard")))
				.andExpect(jsonPath("$._links.streams").doesNotExist())
				.andExpect(jsonPath("$._links.runtime").doesNotExist())
				.andExpect(jsonPath("$._links.metrics").doesNotExist())
				.andExpect(jsonPath("$._links.tasks").doesNotExist())
				.andExpect(jsonPath("$._links.jobs").doesNotExist())
				.andExpect(jsonPath("$._links.tools/parseTaskTextToGraph").doesNotExist())
				.andExpect(jsonPath("$._links.tools/convertTaskGraphToText").doesNotExist())
				.andExpect(jsonPath("$._links.counters/counter.href", is("http://localhost/metrics/counters/{name}")))
				.andExpect(jsonPath("$._links.field-value-counters.href", is("http://localhost/metrics/field-value-counters")))
				.andExpect(jsonPath("$._links.field-value-counters/counter.href", is("http://localhost/metrics/field-value-counters/{name}")))
				.andExpect(jsonPath("$._links.aggregate-counters.href", is("http://localhost/metrics/aggregate-counters")))
				.andExpect(jsonPath("$._links.aggregate-counters/counter.href", is("http://localhost/metrics/aggregate-counters/{name}")))
				.andExpect(jsonPath("$._links.apps.href", is("http://localhost/apps")))
				.andExpect(jsonPath("$._links.about.href", is("http://localhost/about")))
				.andExpect(jsonPath("$._links.completions/stream.href", is("http://localhost/completions/stream{?start,detailLevel}")))
				.andExpect(jsonPath("$._links.completions/task.href", is("http://localhost/completions/task{?start,detailLevel}")));
	}
}
