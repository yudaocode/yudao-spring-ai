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

package org.springframework.ai.vectorstore.redis.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that vector storage does not replace or require the application's Redis
 * driver.
 *
 * @author 芋道源码
 */
class RedisVectorStoreConnectionFallbackTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(RedisVectorStoreAutoConfiguration.class))
		.withBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class))
		.withPropertyValues("spring.ai.vectorstore.redis.initialize-schema=false",
				"spring.ai.vectorstore.redis.dimensions=3", "spring.redis.host=127.0.0.1", "spring.redis.port=1",
				"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1");

	@Test
	void nativePropertiesSupportVectorStorageWithoutAJedisFactory() {
		this.runner.run(context -> assertThat(context).hasSingleBean(RedisVectorStore.class));
	}

	@Test
	void clusterCannotFallBackToAnUnrelatedStandaloneServer() {
		this.runner
			.withPropertyValues("spring.redis.cluster.nodes=127.0.0.1:16379",
					"spring.data.redis.cluster.nodes=127.0.0.1:16379")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
					.hasStackTraceContaining("Redis Cluster or Sentinel");
			});
	}

	@Test
	void sentinelCannotFallBackToAnUnrelatedStandaloneServer() {
		this.runner
			.withPropertyValues("spring.redis.sentinel.master=local-master",
					"spring.redis.sentinel.nodes=127.0.0.1:26379", "spring.data.redis.sentinel.master=local-master",
					"spring.data.redis.sentinel.nodes=127.0.0.1:26379")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
					.hasStackTraceContaining("Redis Cluster or Sentinel");
			});
	}

	@Test
	void existingApplicationRedisFactoryIsPreserved() {
		RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
		this.runner.withBean(RedisConnectionFactory.class, () -> factory).run(context -> {
			assertThat(context).hasSingleBean(RedisVectorStore.class);
			assertThat(context.getBean(RedisConnectionFactory.class)).isSameAs(factory);
			assertThat(context).doesNotHaveBean(JedisConnectionFactory.class);
		});
	}

	@Test
	void existingJedisFactoryIsStillSupported() {
		JedisConnectionFactory factory = mock(JedisConnectionFactory.class);
		when(factory.getHostName()).thenReturn("127.0.0.1");
		when(factory.getPort()).thenReturn(1);
		this.runner.withBean(JedisConnectionFactory.class, () -> factory)
			.run(context -> assertThat(context).hasSingleBean(RedisVectorStore.class));
	}

}
