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

package org.springframework.ai.compat.jdkhttp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.BiPredicate;

/**
 * @author 芋道源码
 */
public final class HttpHeaders {

	private final Map<String, List<String>> values;

	private HttpHeaders(Map<String, List<String>> values) {
		this.values = values;
	}

	public static HttpHeaders of(Map<String, List<String>> source, BiPredicate<String, String> filter) {
		Objects.requireNonNull(filter);
		Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		source.forEach((key, entries) -> {
			String name = Objects.requireNonNull(key);
			List<String> accepted = copy.computeIfAbsent(name, k -> new ArrayList<>());
			for (String entry : entries) {
				Objects.requireNonNull(entry);
				if (filter.test(key, entry)) {
					accepted.add(entry);
				}
			}
		});
		copy.entrySet().removeIf(entry -> entry.getValue().isEmpty());
		copy.replaceAll((key, entries) -> Collections.unmodifiableList(entries));
		return new HttpHeaders(Collections.unmodifiableMap(copy));
	}

	public Optional<String> firstValue(String name) {
		List<String> v = allValues(name);
		return v.isEmpty() ? Optional.empty() : Optional.of(v.get(0));
	}

	public OptionalLong firstValueAsLong(String name) {
		Optional<String> v = firstValue(name);
		return v.isPresent() ? OptionalLong.of(Long.parseLong(v.get())) : OptionalLong.empty();
	}

	public List<String> allValues(String name) {
		return this.values.getOrDefault(Objects.requireNonNull(name), Collections.emptyList());
	}

	public Map<String, List<String>> map() {
		return this.values;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof HttpHeaders && this.values.equals(((HttpHeaders) o).values);
	}

	@Override
	public int hashCode() {
		return this.values.entrySet()
			.stream()
			.mapToInt(entry -> entry.getKey().toLowerCase(Locale.ROOT).hashCode() ^ entry.getValue().hashCode())
			.sum();
	}

	@Override
	public String toString() {
		return this.values.toString();
	}

}
