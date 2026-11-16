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

package org.springframework.ai.vectorstore.neo4j.compat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author 芋道源码
 */
class SchemaNamesCompatibilityTests {

	@Test
	void escapingMatchesTheOfficialJava17Implementation() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		int rows = 0;
		try (BufferedReader input = new BufferedReader(new InputStreamReader(
				getClass().getResourceAsStream("/schema-names-java17-oracle.tsv"), StandardCharsets.UTF_8))) {
			String row;
			while ((row = input.readLine()) != null) {
				String[] fields = row.split("\t", -1);
				String value = decode(fields[0]);
				Optional<String> actual = SchemaNames.sanitize(value, Boolean.parseBoolean(fields[1]),
						Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
				assertEquals(Optional.ofNullable(decode(fields[4])), actual, "official fixture row " + rows);
				rows++;
			}
		}
		assertEquals(434, rows);
	}

	private static String decode(String value) {
		return value.equals("~") ? null : new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
	}

}
