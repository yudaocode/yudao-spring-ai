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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Java 8 implementations of the immutable collection factories used by upstream. Null
 * rejection, duplicate handling and defensive copying match the JDK factories.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class Java8Collections {

	private Java8Collections() {
	}

	public static <E> List<E> streamToList(java.util.stream.Stream<E> source) {
		return Collections.unmodifiableList(source.collect(java.util.stream.Collectors.toList()));
	}

	public static <E> java.util.stream.Collector<E, ?, List<E>> toUnmodifiableList() {
		return java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),
				Java8Collections::listCopyOf);
	}

	public static <E> java.util.stream.Collector<E, ?, Set<E>> toUnmodifiableSet() {
		return java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),
				Java8Collections::setCopyOf);
	}

	public static <T, K, V> java.util.stream.Collector<T, ?, Map<K, V>> toUnmodifiableMap(
			java.util.function.Function<? super T, ? extends K> key,
			java.util.function.Function<? super T, ? extends V> value) {
		return java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toMap(key, value),
				Java8Collections::mapCopyOf);
	}

	public static <T, K, V> java.util.stream.Collector<T, ?, Map<K, V>> toUnmodifiableMap(
			java.util.function.Function<? super T, ? extends K> key,
			java.util.function.Function<? super T, ? extends V> value, java.util.function.BinaryOperator<V> merge) {
		return java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toMap(key, value, merge),
				Java8Collections::mapCopyOf);
	}

	@SafeVarargs
	public static <E> List<E> listOf(E... elements) {
		List<E> result = new ArrayList<>(elements.length);
		for (E element : elements) {
			result.add(Objects.requireNonNull(element));
		}
		return Collections.unmodifiableList(result);
	}

	public static <E> List<E> listCopyOf(Collection<? extends E> source) {
		List<E> result = new ArrayList<>(source.size());
		for (E element : source) {
			result.add(Objects.requireNonNull(element));
		}
		return Collections.unmodifiableList(result);
	}

	@SafeVarargs
	public static <E> Set<E> setOf(E... elements) {
		Set<E> result = new LinkedHashSet<>();
		for (E element : elements) {
			if (!result.add(Objects.requireNonNull(element))) {
				throw new IllegalArgumentException("Duplicate element: " + element);
			}
		}
		return Collections.unmodifiableSet(result);
	}

	public static <E> Set<E> setCopyOf(Collection<? extends E> source) {
		Set<E> result = new LinkedHashSet<>();
		for (E element : source) {
			result.add(Objects.requireNonNull(element));
		}
		return Collections.unmodifiableSet(result);
	}

	public static <K, V> Map<K, V> mapOf(K k1, V v1) {
		return Java8Collections.<K, V>mapOf(new Object[] { k1, v1 });
	}

	public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
		return Java8Collections.<K, V>mapOf(new Object[] { k1, v1, k2, v2 });
	}

	public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
		return Java8Collections.<K, V>mapOf(new Object[] { k1, v1, k2, v2, k3, v3 });
	}

	public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
		return Java8Collections.<K, V>mapOf(new Object[] { k1, v1, k2, v2, k3, v3, k4, v4 });
	}

	public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
		return Java8Collections.<K, V>mapOf(new Object[] { k1, v1, k2, v2, k3, v3, k4, v4, k5, v5 });
	}

	public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
		return Java8Collections.<K, V>mapOf(new Object[] { k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6 });
	}

	public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7,
			V v7) {
		return Java8Collections.<K, V>mapOf(new Object[] { k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7 });
	}

	public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7,
			V v7, K k8, V v8) {
		return Java8Collections
			.<K, V>mapOf(new Object[] { k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8 });
	}

	public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7,
			V v7, K k8, V v8, K k9, V v9) {
		return Java8Collections
			.<K, V>mapOf(new Object[] { k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9 });
	}

	public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7,
			V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
		return Java8Collections.<K, V>mapOf(
				new Object[] { k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10 });
	}

	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> mapOf(Object... entries) {
		if (entries.length % 2 != 0) {
			throw new IllegalArgumentException("Expected key/value pairs");
		}
		Map<K, V> result = new LinkedHashMap<>();
		for (int i = 0; i < entries.length; i += 2) {
			putUnique(result, (K) entries[i], (V) entries[i + 1]);
		}
		return Collections.unmodifiableMap(result);
	}

	@SafeVarargs
	public static <K, V> Map<K, V> mapOfEntries(Map.Entry<? extends K, ? extends V>... entries) {
		Map<K, V> result = new LinkedHashMap<>();
		for (Map.Entry<? extends K, ? extends V> entry : entries) {
			putUnique(result, entry.getKey(), entry.getValue());
		}
		return Collections.unmodifiableMap(result);
	}

	public static <K, V> Map<K, V> mapCopyOf(Map<? extends K, ? extends V> source) {
		Map<K, V> result = new LinkedHashMap<>();
		source.forEach((key, value) -> putUnique(result, key, value));
		return Collections.unmodifiableMap(result);
	}

	public static <K, V> Map.Entry<K, V> mapEntry(K key, V value) {
		return new AbstractMap.SimpleImmutableEntry<>(Objects.requireNonNull(key), Objects.requireNonNull(value));
	}

	private static <K, V> void putUnique(Map<K, V> result, K key, V value) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(value);
		if (result.putIfAbsent(key, value) != null) {
			throw new IllegalArgumentException("Duplicate key: " + key);
		}
	}

}
