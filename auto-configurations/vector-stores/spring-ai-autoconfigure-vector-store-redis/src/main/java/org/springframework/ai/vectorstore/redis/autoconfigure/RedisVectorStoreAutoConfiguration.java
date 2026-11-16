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

import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

/**
 * {@link AutoConfiguration Auto-configuration} for Redis Vector Store.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Jihoon Kim
 * @author Brian Sam-Bodden
 * @author Yanming Zhou
 */
@AutoConfiguration
@ConditionalOnClass({ JedisPooled.class, JedisConnectionFactory.class, RedisVectorStore.class, EmbeddingModel.class })
@EnableConfigurationProperties({ RedisVectorStoreProperties.class, RedisProperties.class })
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.REDIS,
		matchIfMissing = true)
public class RedisVectorStoreAutoConfiguration {

	/**
	 * Creates a default batching strategy for the vector store.
	 * @return a token count batching strategy
	 */
	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	/**
	 * Creates a Redis vector store.
	 * @param embeddingModel the embedding model
	 * @param properties the Redis vector store properties
	 * @param jedisConnectionFactories the optional Jedis connection factories
	 * @param redisProperties the native Boot Redis connection properties
	 * @param observationRegistry the observation registry
	 * @param convention the custom observation convention
	 * @param metadataFields the custom metadata fields
	 * @param batchingStrategy the batching strategy
	 * @return the configured Redis vector store
	 */
	@Bean
	@ConditionalOnMissingBean
	public RedisVectorStore vectorStore(final EmbeddingModel embeddingModel,
			final RedisVectorStoreProperties properties,
			final ObjectProvider<JedisConnectionFactory> jedisConnectionFactories,
			final RedisProperties redisProperties, final ObjectProvider<ObservationRegistry> observationRegistry,
			final ObjectProvider<VectorStoreObservationConvention> convention,
			final ObjectProvider<List<RedisVectorStore.MetadataField>> metadataFields,
			final BatchingStrategy batchingStrategy) {

		JedisConnectionFactory factory = jedisConnectionFactories.getIfAvailable();
		JedisPooled jedisClient = factory != null ? jedisClient(factory) : jedisClient(redisProperties);
		RedisVectorStore.Builder builder = RedisVectorStore.builder(jedisClient, embeddingModel)
			.initializeSchema(properties.isInitializeSchema())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(convention.getIfAvailable())
			.batchingStrategy(batchingStrategy)
			.indexName(properties.getIndexName())
			.prefix(properties.getPrefix())
			.metadataFields(metadataFields.getIfAvailable(List::of));
		if (properties.getDimensions() != null) {
			builder.dimensions(properties.getDimensions());
		}

		// Configure HNSW parameters if available
		hnswConfiguration(builder, properties);

		return builder.build();
	}

	/**
	 * Configures the HNSW-related parameters on the builder.
	 * @param builder the Redis vector store builder
	 * @param properties the Redis vector store properties
	 */
	private void hnswConfiguration(final RedisVectorStore.Builder builder,
			final RedisVectorStoreProperties properties) {
		builder.hnswM(properties.getHnsw().getM())
			.hnswEfConstruction(properties.getHnsw().getEfConstruction())
			.hnswEfRuntime(properties.getHnsw().getEfRuntime());
	}

	private JedisPooled jedisClient(final JedisConnectionFactory jedisConnectionFactory) {

		String host = jedisConnectionFactory.getHostName();
		int port = jedisConnectionFactory.getPort();

		JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
			.ssl(jedisConnectionFactory.isUseSsl())
			.clientName(jedisConnectionFactory.getClientName())
			.connectionTimeoutMillis(jedisConnectionFactory.getTimeout())
			.socketTimeoutMillis(jedisConnectionFactory.getTimeout())
			.password(jedisConnectionFactory.getPassword())
			.build();

		return new JedisPooled(new redis.clients.jedis.HostAndPort(host, port), clientConfig);
	}

	private JedisPooled jedisClient(final RedisProperties properties) {
		if (properties.getCluster() != null || properties.getSentinel() != null) {
			throw new IllegalArgumentException("Redis vector store standalone fallback does not support "
					+ "Redis Cluster or Sentinel; configure a compatible Redis vector-store client explicitly");
		}
		String host = properties.getHost();
		int port = properties.getPort();
		String username = properties.getUsername();
		String password = properties.getPassword();
		boolean ssl = properties.isSsl();
		if (org.springframework.util.StringUtils.hasText(properties.getUrl())) {
			java.net.URI uri = java.net.URI.create(properties.getUrl());
			host = uri.getHost();
			port = uri.getPort() == -1 ? 6379 : uri.getPort();
			ssl = "rediss".equalsIgnoreCase(uri.getScheme());
			if (uri.getUserInfo() != null) {
				String[] credentials = uri.getUserInfo().split(":", 2);
				username = credentials.length == 2 ? credentials[0] : null;
				password = credentials.length == 2 ? credentials[1] : credentials[0];
			}
		}
		int timeout = properties.getTimeout() == null ? 2000 : Math.toIntExact(properties.getTimeout().toMillis());
		// Redis Search indexes are supported on DB 0; application cache DBs are
		// independent.
		JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
			.ssl(ssl)
			.clientName(properties.getClientName())
			.connectionTimeoutMillis(timeout)
			.socketTimeoutMillis(timeout)
			.user(username != null && !username.isEmpty() ? username : null)
			.password(password != null && !password.isEmpty() ? password : null)
			.build();
		return new JedisPooled(new redis.clients.jedis.HostAndPort(host, port), clientConfig);
	}

}
