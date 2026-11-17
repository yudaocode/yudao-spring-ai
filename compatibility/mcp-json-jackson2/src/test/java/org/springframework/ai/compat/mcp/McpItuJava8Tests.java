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
package org.springframework.ai.compat.mcp;

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import com.ethlo.time.ITU;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the actual MCP schema runtime and its unique Java 8-safe ITU provider.
 *
 * @author 芋道源码
 */
class McpItuJava8Tests {

	@Test
	void onlyOneJava8SafeItuImplementationIsOnTheClasspath() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		Enumeration<URL> resources = ITU.class.getClassLoader().getResources("com/ethlo/time/ITU.class");
		List<URL> origins = Collections.list(resources);
		assertEquals(1, origins.size(), origins.toString());
		assertTrue(origins.get(0).toString().contains("spring-ai-itu-compat"), origins.toString());
		assertEquals(93784, ITU.parseDuration("P1DT2H3M4.5S").getSeconds());
	}

	@Test
	void networkntDurationAssertionsAcceptValidValuesAndRejectInvalidOnJava8() throws Exception {
		Schema schema = SchemaRegistry.withDefaultDialect(Dialects.getDraft202012())
			.getSchema("{\"type\":\"string\",\"format\":\"duration\"}", InputFormat.JSON);
		ObjectMapper json = new ObjectMapper();
		assertTrue(schema
			.validate(json.readTree("\"P1DT2H3M4S\""),
					(java.util.function.Consumer<com.networknt.schema.ExecutionContext>) context -> context
						.executionConfig(config -> config.formatAssertionsEnabled(true)))
			.isEmpty());
		assertFalse(schema
			.validate(json.readTree("\"not-a-duration\""),
					(java.util.function.Consumer<com.networknt.schema.ExecutionContext>) context -> context
						.executionConfig(config -> config.formatAssertionsEnabled(true)))
			.isEmpty());
	}

}
