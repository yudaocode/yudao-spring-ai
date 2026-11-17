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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.util.ConnectionDetails;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves real Compose startup, SPI/JSON parsing and application-exit shutdown on native8.
 *
 * @author 芋道源码
 */
@Timeout(100)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "compat.devservices.tests", matches = "true")
class Boot2DockerComposeRuntimeIT {

	@TempDir
	Path temporary;

	@Test
	void childVmStartsServiceConnectsOverHttpAndShutsDownItsOwnProject() throws Exception {
		runCase(false);
	}

	@Test
	void childVmDoesNotStopComposeServicesWhichWereAlreadyRunning() throws Exception {
		runCase(true);
	}

	private void runCase(boolean preexisting) throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		Path directory = Files.createDirectory(temporary.resolve("compose fixture café"));
		Path compose = directory.resolve("compose.yaml");
		Path proof = directory.resolve("native8-proof.txt");
		String project = "native8compose" + UUID.randomUUID().toString().replace("-", "");
		String yaml = "name: " + project
				+ "\nservices:\n  web:\n    image: nginx:1.28-alpine\n    ports:\n      - target: 80\n        published: \"0\"\n        host_ip: 127.0.0.1\n        protocol: tcp\n    labels:\n      org.springframework.boot.service-connection: nginx\n      org.yudao.compat.task: boot2-compose-probe\n";
		Files.write(compose, yaml.getBytes(StandardCharsets.UTF_8));
		Path log = directory.resolve("child.log");
		Process child = null;
		try {
			if (preexisting) {
				Process start = new ProcessBuilder("docker", "compose", "--file", compose.toString(), "up", "--detach",
						"--wait")
					.redirectErrorStream(true)
					.redirectOutput(directory.resolve("prestart.log").toFile())
					.start();
				assertTrue(start.waitFor(30, TimeUnit.SECONDS));
				assertEquals(0, start.exitValue());
			}
			child = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-Djava.net.preferIPv4Stack=true",
					"-cp", System.getProperty("java.class.path"), ChildMain.class.getName(), compose.toString(),
					proof.toString())
				.redirectErrorStream(true)
				.redirectOutput(log.toFile())
				.start();
			assertTrue(child.waitFor(60, TimeUnit.SECONDS), "Native8 application exit timed out: " + log);
			assertEquals(0, child.exitValue(), new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
			assertEquals("1.8:200", new String(Files.readAllBytes(proof), StandardCharsets.UTF_8));
			Path ps = directory.resolve("ps.txt");
			Process inspect = new ProcessBuilder("docker", "compose", "--file", compose.toString(), "ps", "--all",
					"--quiet")
				.redirectErrorStream(true)
				.redirectOutput(ps.toFile())
				.start();
			assertTrue(inspect.waitFor(15, TimeUnit.SECONDS));
			assertEquals(0, inspect.exitValue());
			boolean noContainers = new String(Files.readAllBytes(ps), StandardCharsets.UTF_8).trim().isEmpty();
			assertEquals(!preexisting, noContainers,
					"VM exit must remove only projects it started; externally started services must remain running");
		}
		finally {
			if (child != null && child.isAlive()) {
				child.destroy();
				if (!child.waitFor(5, TimeUnit.SECONDS)) {
					child.destroyForcibly();
					child.waitFor(5, TimeUnit.SECONDS);
				}
			}
			Process cleanup = new ProcessBuilder("docker", "compose", "--file", compose.toString(), "down", "--volumes",
					"--remove-orphans")
				.redirectErrorStream(true)
				.redirectOutput(directory.resolve("cleanup.log").toFile())
				.start();
			assertTrue(cleanup.waitFor(20, TimeUnit.SECONDS));
			assertEquals(0, cleanup.exitValue());
		}
	}

	/**
	 * @author 芋道源码
	 */
	public static class ChildMain {

		public static void main(String[] args) throws Exception {
			if (!"1.8".equals(System.getProperty("java.specification.version"))) {
				throw new IllegalStateException("Expected native8");
			}
			ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
				.web(WebApplicationType.NONE)
				.properties("spring.main.banner-mode=off", "logging.level.root=WARN",
						"spring.docker.compose.file=" + args[0], "spring.docker.compose.skip.in-tests=false",
						"spring.docker.compose.stop.command=down", "spring.docker.compose.stop.timeout=5s")
				.run();
			ProbeDetails details = context.getBean(ProbeDetails.class);
			HttpURLConnection connection = (HttpURLConnection) new URL(
					"http://" + details.host() + ":" + details.port())
				.openConnection();
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			try {
				if (connection.getResponseCode() != 200) {
					throw new IllegalStateException("HTTP did not succeed");
				}
				try (InputStream input = connection.getInputStream()) {
					byte[] bytes = new byte[4096];
					int read = input.read(bytes);
					if (!new String(bytes, 0, read, StandardCharsets.UTF_8).contains("Welcome to nginx!")) {
						throw new IllegalStateException("Unexpected body");
					}
				}
				Files.write(java.nio.file.Paths.get(args[1]), "1.8:200".getBytes(StandardCharsets.UTF_8));
			}
			finally {
				connection.disconnect();
			}
			System.exit(0);
		}

	}

	/**
	 * @author 芋道源码
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class App {

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
	public static class NginxFactory extends DockerComposeConnectionDetailsFactory<ProbeDetails> {

		public NginxFactory() {
			super("nginx");
		}

		@Override
		protected ProbeDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
			return new Details(source.getRunningService());
		}

		/**
		 * @author 芋道源码
		 */
		private static class Details extends DockerComposeConnectionDetails implements ProbeDetails {

			private final RunningService service;

			Details(RunningService service) {
				super(service);
				this.service = service;
			}

			public String host() {
				return service.host();
			}

			public int port() {
				return service.ports().get(80);
			}

		}

	}

}
