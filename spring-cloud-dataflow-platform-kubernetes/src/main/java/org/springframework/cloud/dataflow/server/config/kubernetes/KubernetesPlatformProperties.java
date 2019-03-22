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

package org.springframework.cloud.dataflow.server.config.kubernetes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesDeployerProperties;

/**
 * @author Ilayaperumal Gopinathan
 */
@ConfigurationProperties("spring.cloud.dataflow.task.platform.kubernetes")
public class KubernetesPlatformProperties {

	private Map<String, KubernetesDeployerProperties> accounts = new LinkedHashMap<>();

	public Map<String, KubernetesDeployerProperties> getAccounts() {
		return accounts;
	}

	public void setAccounts(Map<String, KubernetesDeployerProperties> accounts) {
		this.accounts = accounts;
	}
}
