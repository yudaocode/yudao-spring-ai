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

import com.tangosol.io.DefaultSerializer;
import com.tangosol.io.Serializer;
import com.tangosol.io.pof.ConfigurablePofContext;
import com.tangosol.run.xml.XmlElement;
import com.tangosol.run.xml.XmlHelper;

/**
 * Carries the actual cache serializer configuration to a legacy aggregator.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class Java8PofConfigDescriptor extends ConfigurablePofContext {

	private Java8PofConfigDescriptor(ConfigurablePofContext context) {
		super(context);
	}

	/**
	 * Describe an initialized or URI-backed POF context.
	 * @param context the context
	 * @return a portable descriptor
	 * @since 2.1.0
	 */
	public static String describe(ConfigurablePofContext context) {
		XmlElement config = context.getConfig();
		return config == null ? "uri:" + new Java8PofConfigDescriptor(context).getConfigLocation()
				: "xml:" + config.toString();
	}

	/**
	 * Restore the serializer on the receiving member.
	 * @param descriptor the descriptor, or null/empty for Java serialization
	 * @return the cache serializer
	 * @since 2.1.0
	 */
	public static Serializer restore(String descriptor) {
		if (descriptor == null || descriptor.isEmpty()) {
			return new DefaultSerializer();
		}
		if (descriptor.startsWith("uri:")) {
			return new ConfigurablePofContext(descriptor.substring(4));
		}
		if (descriptor.startsWith("xml:")) {
			return new ConfigurablePofContext(XmlHelper.loadXml(descriptor.substring(4)));
		}
		throw new IllegalArgumentException("Unknown Coherence serializer descriptor");
	}

}
