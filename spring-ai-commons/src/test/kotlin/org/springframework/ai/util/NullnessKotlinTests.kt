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
package org.springframework.ai.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Kotlin nullability checks for the Spring 6 compatibility implementation.
 * @author 芋道源码
 */
class NullnessKotlinTests {

    @Test
    fun `non nullable default parameter remains non nullable`() {
        val parameter = Fixture::class.java.getDeclaredMethod("withDefault", String::class.java).parameters[0]
        assertThat(Nullness.forParameter(parameter)).isEqualTo(Nullness.NON_NULL)
    }

    @Test
    fun `nullable constructor parameter is nullable`() {
        val constructor = Fixture::class.java.getDeclaredConstructor(String::class.java, String::class.java)
        assertThat(Nullness.forParameter(constructor.parameters[0])).isEqualTo(Nullness.NON_NULL)
        assertThat(Nullness.forParameter(constructor.parameters[1])).isEqualTo(Nullness.NULLABLE)
    }

    @Test
    fun `nullable field and accessor are nullable`() {
        assertThat(Nullness.forField(Fixture::class.java.getDeclaredField("optional"))).isEqualTo(Nullness.NULLABLE)
        assertThat(Nullness.forMethodReturnType(Fixture::class.java.getDeclaredMethod("getOptional")))
            .isEqualTo(Nullness.NULLABLE)
        assertThat(Nullness.forParameter(Fixture::class.java.getDeclaredMethod("setOptional", String::class.java).parameters[0]))
            .isEqualTo(Nullness.NULLABLE)
    }

    @Test
    fun `non nullable field and nullable function return retain their types`() {
        assertThat(Nullness.forField(Fixture::class.java.getDeclaredField("required"))).isEqualTo(Nullness.NON_NULL)
        assertThat(Nullness.forMethodReturnType(Fixture::class.java.getDeclaredMethod("nullableReturn")))
            .isEqualTo(Nullness.NULLABLE)
    }

    class Fixture(val required: String, var optional: String?) {
        fun withDefault(value: String = "value"): String = value
        fun nullableReturn(): String? = optional
    }
}
