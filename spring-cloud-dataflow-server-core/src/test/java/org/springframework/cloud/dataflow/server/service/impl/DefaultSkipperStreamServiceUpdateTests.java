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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.server.audit.service.AuditRecordService;
import org.springframework.cloud.dataflow.server.config.features.FeaturesProperties;
import org.springframework.cloud.dataflow.server.configuration.TestDependencies;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.StreamValidationService;
import org.springframework.cloud.dataflow.server.stream.SkipperStreamDeployer;
import org.springframework.cloud.dataflow.server.support.PlatformUtils;
import org.springframework.cloud.dataflow.server.support.TestResourceUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @author Gunnar Hillert
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestDependencies.class)
@TestPropertySource(properties = {
		FeaturesProperties.FEATURES_PREFIX + "." + FeaturesProperties.SKIPPER_ENABLED + "=true" })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DefaultSkipperStreamServiceUpdateTests {

	@Autowired
	private SkipperStreamDeployer skipperStreamDeployer;

	@Autowired
	AppDeploymentRequestCreator appDeploymentRequestCreator;

	@Autowired
	private AuditRecordService auditRecordService;

	@Autowired
	private StreamDefinitionRepository streamDefinitionRepository;

	@Autowired
	private AppRegistryService appRegistryService;

	@Autowired
	private StreamValidationService streamValidationService;

	@Test
	public void testCreateUpdateRequestsWithRegisteredApp() throws IOException {
		this.appRegistryService.save("log", ApplicationType.sink, "1.1.1.RELEASE",
				URI.create("maven://org.springframework.cloud.stream.app:log-sink-rabbit:jar:1.0.0.BUILD-SNAPSHOT"),
				null);
		testCreateUpdateRequests();
	}

	@Test
	public void testCreateUpdateRequestsWithoutRegisteredApp() throws IOException {
		try {
			testCreateUpdateRequests();
			fail("IllegalStateException is expected.");
		}
		catch (IllegalStateException e) {
			assertThat(e.getMessage()).isEqualTo("The log:sink:1.1.1.RELEASE app is not registered!");
		}
	}

	public void testCreateUpdateRequests() throws IOException {

		DefaultSkipperStreamService streamService = new DefaultSkipperStreamService(streamDefinitionRepository,
				skipperStreamDeployer,
				appDeploymentRequestCreator, streamValidationService, auditRecordService);
		StreamDefinition streamDefinition = new StreamDefinition("test", "time | log");
		this.streamDefinitionRepository.save(streamDefinition);
		Map<String, String> updateProperties = new HashMap<>();
		updateProperties.put("app.log.server.port", "9999");
		updateProperties.put("app.log.endpoints.sensitive", "false");
		updateProperties.put("app.log.level", "ERROR"); // this should be expanded
		updateProperties.put("deployer.log.memory", "4096m");
		updateProperties.put("version.log", "1.1.1.RELEASE");
		String yml = streamService.convertPropertiesToSkipperYaml(streamDefinition, updateProperties);

		String expectedYaml = StreamUtils.copyToString(
				TestResourceUtils.qualifiedResource(getClass(), "update.yml").getInputStream(),
				Charset.defaultCharset());
		if (PlatformUtils.isWindows()) {
			expectedYaml = expectedYaml + DumperOptions.LineBreak.WIN.getString();
		}

		DumperOptions dumperOptions = new DumperOptions();
		dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		dumperOptions.setPrettyFlow(true);
		Yaml yaml = new Yaml(dumperOptions);

		Object actualYamlLoaded = yaml.load(yml);
		Object expectedYamlLoaded = yaml.load(expectedYaml);
		assertThat(actualYamlLoaded).isEqualTo(expectedYamlLoaded);
	}
}
