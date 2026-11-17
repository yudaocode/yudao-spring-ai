/*
 * Copyright 2023-present the original author or authors.
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

package cn.iocoder.ai.compatibility;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures disabling the integration avoids even locating an invalid Compose file.
 *
 * @author 芋道源码
 */
class DockerComposeDisabledJava8Tests {

	@Test
	void disabledIntegrationStartsWithoutAComposeFileOrServiceConnections() {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
			.web(WebApplicationType.NONE)
			.properties("spring.main.banner-mode=off", "logging.level.root=WARN", "spring.docker.compose.enabled=false",
					"spring.docker.compose.skip.in-tests=false",
					"spring.docker.compose.file=absent-native8-compose-file-for-disable-contract.yaml")
			.run()) {
			assertTrue(context.isActive());
			assertTrue(context.getBeansOfType(Boot2DockerComposeRuntimeIT.ProbeDetails.class).isEmpty());
		}
	}

	/**
	 * @author 芋道源码
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class App {

	}

}
