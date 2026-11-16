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

package org.springframework.ai.util;

import org.springframework.util.ClassUtils;

/**
 * Spring 6 simple-value classification on the Spring 5 runtime. Mirrors
 * ClassUtils.isSimpleValueType from Spring Framework 6.2.19.
 *
 * @author 芋道源码
 */
public final class Java8SpringTypes {

	private static final Class<?>[] SIMPLE_TYPES = { Enum.class, CharSequence.class, Number.class, java.util.Date.class,
			java.time.temporal.Temporal.class, java.time.ZoneId.class, java.util.TimeZone.class, java.io.File.class,
			java.nio.file.Path.class, java.nio.charset.Charset.class, java.util.Currency.class,
			java.net.InetAddress.class, java.net.URI.class, java.net.URL.class, java.util.UUID.class,
			java.util.Locale.class, java.util.regex.Pattern.class, Class.class };

	private Java8SpringTypes() {
	}

	public static boolean isSimpleValueType(Class<?> type) {
		if (type == Void.class || type == void.class) {
			return false;
		}
		if (ClassUtils.isPrimitiveOrWrapper(type)) {
			return true;
		}
		for (Class<?> candidate : SIMPLE_TYPES) {
			if (candidate.isAssignableFrom(type)) {
				return true;
			}
		}
		return false;
	}

}
