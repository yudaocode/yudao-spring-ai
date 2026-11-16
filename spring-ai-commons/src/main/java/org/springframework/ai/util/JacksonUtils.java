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

import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Utility methods for Jackson.
 *
 * <p>
 * The default {@link JsonMapper} returned by {@link #getDefaultJsonMapper} can be
 * customized via the automatic discovery of services of type {@link Module} by JDK
 * {@link java.util.ServiceLoader} facility.
 *
 * @author Sebastien Deleuze
 */
public abstract class JacksonUtils {

	private static final JsonMapper jsonMapper;

	static {
		jsonMapper = JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			.addModules(JacksonUtils.instantiateAvailableModules())
			.build();
	}

	/**
	 * Return the Jackson modules found by {@link MapperBuilder#findModules(ClassLoader)}
	 * using JDK {@link java.util.ServiceLoader} facility.
	 * @return The list of instantiated modules.
	 */
	public static List<Module> instantiateAvailableModules() {
		return MapperBuilder.findModules(JacksonUtils.class.getClassLoader());
	}

	/**
	 * Returns a default Jackson {@link JsonMapper} instance customized with
	 * {@link DeserializationFeature#FAIL_ON_TRAILING_TOKENS} disabled and the Jackson
	 * modules found by {@link #instantiateAvailableModules} configured.
	 * @since 2.0.0
	 */
	public static JsonMapper getDefaultJsonMapper() {
		return jsonMapper;
	}

}
