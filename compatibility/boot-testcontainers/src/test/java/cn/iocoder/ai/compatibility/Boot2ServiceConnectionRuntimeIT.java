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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;

import org.springframework.ai.util.ConnectionDetails;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts and stops a real container through the backported Boot2 lifecycle and SPI.
 *
 * @author 芋道源码
 */
@Timeout(120)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "compat.devservices.tests", matches = "true")
class Boot2ServiceConnectionRuntimeIT {

	@Test
	void beanServiceConnectionUsesStartedContainerAndStopsItOnContextClose() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		GenericContainer<?> container = null;
		try {
			try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
				.web(WebApplicationType.NONE)
				.properties("spring.main.banner-mode=off", "logging.level.root=WARN")
				.run()) {
				container = context.getBean(GenericContainer.class);
				assertTrue(container.isRunning());
				ProbeDetails details = context.getBean(ProbeDetails.class);
				assertEquals(container.getMappedPort(80), details.port());
				HttpURLConnection connection = (HttpURLConnection) new URL(
						"http://" + details.host() + ":" + details.port())
					.openConnection();
				connection.setConnectTimeout(5000);
				connection.setReadTimeout(5000);
				try {
					assertEquals(200, connection.getResponseCode());
					try (InputStream input = connection.getInputStream()) {
						byte[] bytes = new byte[4096];
						int read = input.read(bytes);
						assertTrue(new String(bytes, 0, read, java.nio.charset.StandardCharsets.UTF_8)
							.contains("Welcome to nginx!"));
					}
				}
				finally {
					connection.disconnect();
				}
			}
			assertFalse(container.isRunning(),
					"Closing the Boot context must stop the container before manual cleanup");
		}
		finally {
			if (container != null && container.isRunning()) {
				container.stop();
			}
		}
	}

	@Test
	void importsStaticContainerAndDynamicPropertiesAndStopsWithContext() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		GenericContainer<?> container = ImportedContainers.web;
		try {
			try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ImportedApp.class)
				.web(WebApplicationType.NONE)
				.properties("spring.main.banner-mode=off", "logging.level.root=WARN")
				.run()) {
				assertTrue(container.isRunning());
				ProbeDetails details = context.getBean(ProbeDetails.class);
				assertEquals(container.getMappedPort(80), details.port());
				assertEquals(details.port(),
						context.getEnvironment().getProperty("compat.imported.port", Integer.class));
			}
			assertFalse(container.isRunning(), "Imported fields must follow the Boot context lifetime");
		}
		finally {
			if (container.isRunning()) {
				container.stop();
			}
		}
	}

	@Test
	void composedBeanAnnotationIsDiscoveredOnceAndFollowsLifecycle() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		GenericContainer<?> container = null;
		try {
			try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MetaApp.class)
				.web(WebApplicationType.NONE)
				.properties("spring.main.banner-mode=off", "logging.level.root=WARN")
				.run()) {
				container = context.getBean(GenericContainer.class);
				assertTrue(container.isRunning());
				assertEquals(1, context.getBeansOfType(ProbeDetails.class).size());
				assertEquals(container.getMappedPort(80), context.getBean(ProbeDetails.class).port());
			}
			assertFalse(container.isRunning(), "Composed service annotations retain automatic cleanup");
		}
		finally {
			if (container != null && container.isRunning()) {
				container.stop();
			}
		}
	}

	/**
	 * @author 芋道源码 */
	@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
	@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
	@ServiceConnection(name = "nginx", type = ProbeDetails.class)
	@interface MetaService {

	}

	/**
	 * @author 芋道源码 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class MetaApp {

		@Bean
		@MetaService
		GenericContainer<?> web() {
			return new GenericContainer<>("nginx:1.28-alpine").withExposedPorts(80)
				.withLabel("org.yudao.compat.task", "boot2-testcontainers-probe");
		}

	}

	/**
	 * @author 芋道源码
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	@org.springframework.boot.testcontainers.context.ImportTestcontainers(ImportedContainers.class)
	static class ImportedApp {

	}

	/**
	 * @author 芋道源码
	 */
	static class ImportedContainers {

		@ServiceConnection(name = "nginx", type = ProbeDetails.class)
		static final GenericContainer<?> web = new GenericContainer<>("nginx:1.28-alpine").withExposedPorts(80)
			.withLabel("org.yudao.compat.task", "boot2-testcontainers-probe");

		@org.springframework.test.context.DynamicPropertySource
		static void properties(org.springframework.test.context.DynamicPropertyRegistry registry) {
			registry.add("compat.imported.port", () -> web.getMappedPort(80));
		}

	}

	/**
	 * @author 芋道源码
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class App {

		@Bean
		@ServiceConnection(name = "nginx")
		GenericContainer<?> web() {
			return new GenericContainer<>("nginx:1.28-alpine").withExposedPorts(80)
				.withLabel("org.yudao.compat.task", "boot2-testcontainers-probe");
		}

	}

	/**
	 * @author 芋道源码
	 */
	public interface ProbeDetails extends ConnectionDetails {

		String host();

		int port();

	}

	/**
	 * @author 芋道源码
	 */
	public static class ProbeFactory extends ContainerConnectionDetailsFactory<GenericContainer<?>, ProbeDetails> {

		public ProbeFactory() {
			super("nginx");
		}

		@Override
		protected ProbeDetails getContainerConnectionDetails(ContainerConnectionSource<GenericContainer<?>> source) {
			return new Details(source);
		}

		/**
		 * @author 芋道源码
		 */
		private static class Details extends ContainerConnectionDetails<GenericContainer<?>> implements ProbeDetails {

			Details(ContainerConnectionSource<GenericContainer<?>> source) {
				super(source);
			}

			public String host() {
				return getContainer().getHost();
			}

			public int port() {
				return getContainer().getMappedPort(80);
			}

		}

	}

}
