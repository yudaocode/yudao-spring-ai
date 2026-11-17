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

package org.springframework.boot.docker.compose.service.connection.test;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;
import org.springframework.boot.testsupport.DisabledIfProcessUnavailable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import static org.assertj.core.api.Assertions.fail;

/**
 * Abstract base class for integration tests.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Scott Frederick
 */
@DisabledIfProcessUnavailable({ "docker", "version" })
@DisabledIfProcessUnavailable({ "docker", "compose" })
public abstract class AbstractDockerComposeIT {

	@TempDir
	private static Path tempDir;

	private final Resource composeResource;

	private final DockerImageName dockerImageName;

	protected AbstractDockerComposeIT(String composeResource, DockerImageName dockerImageName) {
		this.composeResource = new ClassPathResource(composeResource, getClass());
		this.dockerImageName = dockerImageName;
	}

	@AfterAll
	static void shutDown() {
		org.springframework.boot.docker.compose.lifecycle.Boot2DockerComposeTestLifecycle.shutdown();
	}

	protected final <T extends ConnectionDetails> T run(Class<T> type) {
		SpringApplication application = new SpringApplication(Config.class);
		org.springframework.boot.docker.compose.lifecycle.Boot2DockerComposeTestLifecycle.configure(application);
		application.setWebApplicationType(WebApplicationType.NONE);
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("spring.docker.compose.skip.in-tests", "false");
		properties.put("spring.docker.compose.file",
				transformedComposeFile(composeResourceFile(), this.dockerImageName));
		properties.put("spring.docker.compose.stop.command", "down");
		application.setDefaultProperties(properties);
		return org.springframework.boot.docker.compose.lifecycle.Boot2DockerComposeTestLifecycle
			.track(application.run())
			.getBean(type);
	}

	private File composeResourceFile() {
		try {
			return this.composeResource.getFile();
		}
		catch (IOException ex) {
			throw new RuntimeException(ex.getMessage(), ex);
		}
	}

	private File transformedComposeFile(File composeFile, DockerImageName imageName) {
		File tempComposeFile = Path.of(tempDir.toString(), composeFile.getName()).toFile();
		try {
			String composeFileContent = FileCopyUtils.copyToString(new FileReader(composeFile));
			composeFileContent = composeFileContent.replace("{imageName}", imageName.asCanonicalNameString());
			FileCopyUtils.copy(composeFileContent, new FileWriter(tempComposeFile));
		}
		catch (IOException ex) {
			fail("Error transforming Docker compose file '" + composeFile + "' to '" + tempComposeFile + "': "
					+ ex.getMessage());
		}
		return tempComposeFile;
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

	}

}
