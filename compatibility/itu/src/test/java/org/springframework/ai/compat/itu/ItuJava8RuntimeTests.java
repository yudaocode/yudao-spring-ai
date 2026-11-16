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
package org.springframework.ai.compat.itu;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import com.ethlo.time.ITU;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies ITU's actual Java 8 API binding rather than only its class-file version.
 *
 * @author 芋道源码
 */
class ItuJava8RuntimeTests {

	@ParameterizedTest
	@CsvSource({ "PT0S,0,0", "P1D,86400,0", "P1W,604800,0", "PT2H,7200,0", "PT0.5S,0,500000000",
			"P1DT2H3M4.5S,93784,500000000", "-PT1S,-1,0" })
	void durationParserUsesJava8MultiplyExact(String input, long seconds, int nanos) {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		com.ethlo.time.Duration result = ITU.parseDuration(input);
		assertEquals(seconds, result.getSeconds());
		assertEquals(nanos, result.getNanos());
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-11-17T03:00:00Z", "2026-11-17T11:00:00+08:00",
			"2026-11-17T03:00:00.123456789Z" })
	void dateTimeBehaviorAndResourceLoadingRemainAvailable(String input) {
		assertEquals(OffsetDateTime.parse(input).toInstant(), ITU.parseDateTime(input).toInstant());
	}

	@Test
	void overflowStillFailsRatherThanWrapping() {
		assertThrows(DateTimeParseException.class, () -> ITU.parseDuration("P999999999999999999999999999D"));
	}

	@Test
	void parsedDurationCanMoveAnInstant() {
		assertEquals(Instant.EPOCH.plusSeconds(86400), ITU.parseDuration("P1D").timeline(Instant.EPOCH));
	}

}
