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

package org.springframework.ai.compat.coherence;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Collections;

import com.oracle.coherence.ai.Float32Vector;
import com.oracle.coherence.ai.hnsw.HnswIndex;
import com.tangosol.net.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the Java8 SDK payload and the sole source of vector classes.
 *
 * @author 芋道源码
 */
class CoherenceJava8PayloadTests {

	@Test
	void shippedClassesAndNativeResourcesStayOnTheJava8Route() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		assertEquals(1,
				Collections
					.list(getClass().getClassLoader().getResources("com/oracle/coherence/ai/Float32Vector.class"))
					.size());
		for (Class<?> type : new Class<?>[] { Float32Vector.class, HnswIndex.class, Session.class }) {
			try (InputStream stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class");
					DataInputStream input = new DataInputStream(stream)) {
				assertEquals(0xCAFEBABE, input.readInt());
				input.readUnsignedShort();
				assertTrue(input.readUnsignedShort() <= 52, type.getName());
			}
		}
		assertNotNull(getClass().getResource("/coherence-ai-java8-pof-config.xml"));
		assertNotNull(getClass().getResource("/libhnswlib-aarch64.dylib"));
		assertNotNull(getClass().getResource("/libhnswlib-x86-64.so"));
		assertNotNull(getClass().getResource("/libhnswlib-x86-64.libw"));
	}

}
