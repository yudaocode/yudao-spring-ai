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

package org.springframework.ai.model.chat.memory.repository.jdbc.autoconfigure;

import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks property binding and real invalid-script startup behavior.
 *
 * @author 芋道源码
 */
@SuppressWarnings("removal")
class JdbcChatMemoryContinueOnErrorTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(InitializationConfiguration.class)
		.withPropertyValues("spring.ai.chat.memory.repository.jdbc.initialize-schema=always",
				"spring.ai.chat.memory.repository.jdbc.schema=classpath:invalid-review-schema.sql");

	@Test
	void falseFailsStartupOnAnInvalidSchema() {
		this.runner.withPropertyValues("spring.ai.chat.memory.repository.jdbc.continue-on-error=false")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void trueContinuesAfterAnInvalidSchema() {
		this.runner.withPropertyValues("spring.ai.chat.memory.repository.jdbc.continue-on-error=true")
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void defaultMatchesTheUpstreamContinueBehavior() {
		this.runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(JdbcChatMemoryRepositoryProperties.class).isContinueOnError()).isTrue();
		});
	}

	/**
	 * @author 芋道源码
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(JdbcChatMemoryRepositoryProperties.class)
	static class InitializationConfiguration {

		@Bean
		DataSource dataSource() {
			JDBCDataSource dataSource = new JDBCDataSource();
			dataSource.setUrl("jdbc:hsqldb:mem:review-" + java.util.UUID.randomUUID());
			dataSource.setUser("sa");
			return dataSource;
		}

		@Bean
		JdbcChatMemoryRepositorySchemaInitializer initializer(DataSource dataSource,
				JdbcChatMemoryRepositoryProperties properties) {
			return new JdbcChatMemoryRepositorySchemaInitializer(dataSource, properties);
		}

	}

}
