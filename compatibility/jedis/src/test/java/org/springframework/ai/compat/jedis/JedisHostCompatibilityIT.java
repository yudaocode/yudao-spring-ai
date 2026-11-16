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

package org.springframework.ai.compat.jedis;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author 芋道源码
 */
class JedisHostCompatibilityIT {

	@Test
	@Timeout(20)
	void bothClientsRunOnJava8WithoutReplacingBoot2Jedis() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		int port;
		try (ServerSocket reserve = new ServerSocket(0)) {
			port = reserve.getLocalPort();
		}
		Path directory = Files.createTempDirectory("jedis-coexistence-");
		Process server = new ProcessBuilder(System.getenv().getOrDefault("REDIS_TEST_BINARY", "redis-server"), "--port",
				Integer.toString(port), "--bind", "127.0.0.1", "--save", "", "--appendonly", "no", "--requirepass",
				"local-test-only", "--dir", directory.toString())
			.redirectErrorStream(true)
			.redirectOutput(directory.resolve("redis.log").toFile())
			.start();
		RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration("127.0.0.1", port);
		configuration.setUsername("default");
		configuration.setPassword(org.springframework.data.redis.connection.RedisPassword.of("local-test-only"));
		JedisConnectionFactory factory = new JedisConnectionFactory(configuration);
		try {
			factory.afterPropertiesSet();
			Exception last = null;
			boolean ready = false;
			for (int i = 0; i < 30; i++) {
				try (RedisConnection connection = factory.getConnection()) {
					assertEquals("PONG", connection.ping());
					ready = true;
					break;
				}
				catch (Exception error) {
					last = error;
					Thread.sleep(50);
				}
			}
			assertTrue(ready, String.valueOf(last));
			try (JedisPooled client = new JedisPooled(new HostAndPort("127.0.0.1", port),
					DefaultJedisClientConfig.builder()
						.user("default")
						.password("local-test-only")
						.database(2)
						.clientName("ai-test")
						.build())) {
				assertEquals("PONG", client.ping());
				assertEquals("OK", client.set("ai-only", "new-client"));
				assertEquals("new-client", client.get("ai-only"));
				try (org.springframework.ai.compat.jedis.internal.Pipeline pipeline = client.pipelined()) {
					pipeline.set("pipelined", "ok");
					assertEquals("OK", pipeline.syncAndReturnAll().get(0));
				}
				assertEquals("ok", client.get("pipelined"));
				try (RedisConnection connection = factory.getConnection()) {
					assertEquals("PONG", connection.ping());
					assertNull(connection.stringCommands().get("ai-only".getBytes("UTF-8")));
				}
			}
			try (JedisPooled client = new JedisPooled("127.0.0.1", port, "default", "local-test-only")) {
				assertEquals("PONG", client.ping());
			}
			assertNotNull(Class.forName("redis.clients.jedis.BinaryJedis"));
			assertNotNull(Class.forName("redis.clients.jedis.JedisShardInfo"));
			assertNotNull(Class.forName("redis.clients.jedis.search.Query"));
		}
		finally {
			factory.destroy();
			server.destroy();
			if (!server.waitFor(3, TimeUnit.SECONDS)) {
				server.destroyForcibly();
			}
		}
	}

}
