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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.modelcontextprotocol.modern.client.transport.HttpClientMcpTransport;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Context-close and partial-construction ownership probes on the actual Java8 runtime.
 *
 * @author 芋道源码
 */
class ModernMcpTransportLifecycleTests {

	private ApplicationContextRunner runner() {
		return new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(McpClientAutoConfiguration.class,
					McpToolCallbackAutoConfiguration.class, McpClientModernAutoConfiguration.class))
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.a.url=http://127.0.0.1:1",
					"spring.ai.mcp.client.streamable-http.connections.a.protocol=MODERN");
	}

	private long threads() {
		return Thread.getAllStackTraces()
			.keySet()
			.stream()
			.filter(t -> t.isAlive() && t.getName().startsWith("mcp-http-"))
			.count();
	}

	private void baseline(long expected) throws Exception {
		long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while (threads() > expected && System.nanoTime() < end) {
			Thread.sleep(20);
		}
		assertEquals(expected, threads(), "modern auto-configured HTTP resources must be destroyed");
	}

	@Test
	void closingTheContextReleasesItsModernTransports() throws Exception {
		long before = threads();
		java.util.ArrayList<NamedModernClientMcpTransport> owned = new java.util.ArrayList<>();
		try {
			runner().run(context -> {
				assertNull(context.getStartupFailure());
				@SuppressWarnings("unchecked")
				List<NamedModernClientMcpTransport> transports = (List<NamedModernClientMcpTransport>) context
					.getBean("modernStreamableHttpTransports");
				owned.addAll(transports);
				Thread.sleep(100);
				assertTrue(threads() > before, "the fixture must observe the owned HTTP reactor");
			});
			baseline(before);
		}
		finally {
			for (NamedModernClientMcpTransport transport : owned) {
				((AutoCloseable) transport.transport()).close();
			}
		}
	}

	@Test
	void failingASecondConnectionReleasesTheFirstTransport() throws Exception {
		long before = threads();
		AtomicInteger configured = new AtomicInteger();
		McpClientCustomizer<HttpClientMcpTransport.Builder> customizer = (name, builder) -> {
			if (configured.incrementAndGet() == 2) {
				throw new IllegalStateException("planned second transport failure");
			}
		};
		runner()
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.b.url=http://127.0.0.1:2",
					"spring.ai.mcp.client.streamable-http.connections.b.protocol=MODERN")
			.withBean("failingTransportCustomizer", McpClientCustomizer.class, () -> customizer)
			.run(context -> {
				assertNotNull(context.getStartupFailure());
				assertTrue(context.getStartupFailure().toString().contains("planned second transport failure"));
			});
		assertEquals(2, configured.get(), "the first transport must be built before the controlled failure");
		baseline(before);
	}

}
