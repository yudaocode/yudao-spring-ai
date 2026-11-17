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

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.testcontainers.OpensearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks real2.1 container configuration and generic API on the Java8 VM without Docker.
 *
 * @author 芋道源码
 */
class OpenSearchContainerJava8Tests {

	@BeforeEach
	void nativeVm() {
		assertEquals("1.8", System.getProperty("java.specification.version"));
	}

	@Test
	void defaultUnsecuredConfigurationAndLegacyTypeArePreserved() throws Exception {
		Configurable container = new Configurable("2.19.3");
		container.configureNow();
		assertFalse(container.isSecurityEnabled());
		assertEquals("true", container.getEnvMap().get("DISABLE_SECURITY_PLUGIN"));
		assertEquals("single-node", container.getEnvMap().get("discovery.type"));
		assertTrue(container.getExposedPorts().contains(9200));
		assertEquals(1, Collections
			.list(getClass().getClassLoader().getResources("org/opensearch/testcontainers/OpensearchContainer.class"))
			.size());
	}

	@Test
	void newerSecuredImagesGetTheirRequiredInitialPassword() {
		Configurable container = new Configurable("2.19.3");
		assertSame(container, container.withSecurityEnabled());
		container.configureNow();
		assertTrue(container.isSecurityEnabled());
		assertEquals(container.getPassword(), container.getEnvMap().get("OPENSEARCH_INITIAL_ADMIN_PASSWORD"));
		assertTrue(container.getPassword().length() > 8);
	}

	@Test
	void configuredPasswordAndDiscoveryModeArePreserved() {
		Configurable container = new Configurable("2.19.3");
		container.withSecurityEnabled()
			.withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "Compat_test-123!")
			.withEnv("discovery.type", "zen");
		container.configureNow();
		assertEquals("Compat_test-123!", container.getPassword());
		assertEquals("zen", container.getEnvMap().get("discovery.type"));
	}

	@Test
	void olderSecuredImagesKeepTheirOriginalPasswordBehavior() {
		Configurable container = new Configurable("2.11.0");
		container.withSecurityEnabled();
		container.configureNow();
		assertEquals("admin", container.getPassword());
		assertFalse(container.getEnvMap().containsKey("OPENSEARCH_INITIAL_ADMIN_PASSWORD"));
	}

	/**
	 * @author 芋道源码
	 */
	private static class Configurable extends OpensearchContainer<Configurable> {

		Configurable(String version) {
			super("opensearchproject/opensearch:" + version);
		}

		void configureNow() {
			super.configure();
		}

	}

}
