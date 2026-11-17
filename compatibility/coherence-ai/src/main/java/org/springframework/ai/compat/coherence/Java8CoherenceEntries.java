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

import com.tangosol.util.BinaryEntry;
import com.tangosol.util.InvocableMap;

/**
 * Implements the entry cast used by later Coherence default methods.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class Java8CoherenceEntries {

	private Java8CoherenceEntries() {
	}

	/**
	 * Cast the entry with the same ClassCastException contract as Coherence20.12.
	 * @param entry the entry
	 * @param <K> key type
	 * @param <V> value type
	 * @return the binary entry
	 * @since 2.1.0
	 */
	@SuppressWarnings("unchecked")
	public static <K, V> BinaryEntry<K, V> asBinaryEntry(InvocableMap.Entry<K, V> entry) {
		return (BinaryEntry<K, V>) entry;
	}

}
