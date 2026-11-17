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

package cn.iocoder.ai.compatibility;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.util.ConnectionDetails;
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactories;
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactory;
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactoryNotFoundException;
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsNotFoundException;
import org.springframework.core.Ordered;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates actual Spring5 factory loading, optional providers and result contracts.
 *
 * @author 芋道源码
 */
class ServiceConnectionRegistryJava8Tests {

	@TempDir
	Path directory;
	static final List<String> calls = new ArrayList<>();

	@BeforeEach
	void nativeVm() {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		calls.clear();
	}

	@Test
	void loadsFactoryAndReturnsTypedDetails() throws Exception {
		try (URLClassLoader loader = factories(Good.class)) {
			Map<Class<?>, ConnectionDetails> details = new ConnectionDetailsFactories(loader).getConnectionDetails("ok",
					true);
			assertEquals(1, details.size());
			assertEquals("ok", ((Details) details.get(Details.class)).value());
			assertThrows(UnsupportedOperationException.class, () -> details.clear());
		}
	}

	@Test
	void rejectsMissingFactoriesWhenRequired() throws Exception {
		try (URLClassLoader loader = factories()) {
			assertThrows(ConnectionDetailsFactoryNotFoundException.class,
					() -> new ConnectionDetailsFactories(loader).getConnectionDetails("ok", true));
		}
	}

	@Test
	void ignoresUnrelatedSourceTypes() throws Exception {
		try (URLClassLoader loader = factories(Good.class)) {
			assertThrows(ConnectionDetailsFactoryNotFoundException.class,
					() -> new ConnectionDetailsFactories(loader).getConnectionDetails(Integer.valueOf(1), true));
		}
	}

	@Test
	void distinguishesNoMatchFromNoFactory() throws Exception {
		try (URLClassLoader loader = factories(Good.class)) {
			assertThrows(ConnectionDetailsNotFoundException.class,
					() -> new ConnectionDetailsFactories(loader).getConnectionDetails("reject", true));
		}
	}

	@Test
	void permitsNoMatchWhenOptional() throws Exception {
		try (URLClassLoader loader = factories(Good.class)) {
			assertTrue(new ConnectionDetailsFactories(loader).getConnectionDetails("reject", false).isEmpty());
		}
	}

	@Test
	void rejectsDuplicateDetailsOfTheSameType() throws Exception {
		try (URLClassLoader loader = factories(Good.class, Duplicate.class)) {
			assertThrows(IllegalStateException.class,
					() -> new ConnectionDetailsFactories(loader).getConnectionDetails("ok", true));
		}
	}

	@Test
	void toleratesMissingOptionalSdkWrappedByBeanInstantiation() throws Exception {
		try (URLClassLoader loader = factories(Missing.class, Good.class)) {
			assertEquals(1, new ConnectionDetailsFactories(loader).getConnectionDetails("ok", true).size());
		}
	}

	@Test
	void toleratesAnUnresolvableOptionalProviderClass() throws Exception {
		try (URLClassLoader loader = factories(Good.class)) {
			Path file = directory.resolve("META-INF/spring.factories");
			String value = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			Files.write(file, value.replace("=", "=missing.optional.Factory,").getBytes(StandardCharsets.UTF_8));
			assertEquals(1, new ConnectionDetailsFactories(loader).getConnectionDetails("ok", true).size());
		}
	}

	@Test
	void invokesFactoriesInSpringOrder() throws Exception {
		try (URLClassLoader loader = factories(Other.class, Good.class)) {
			assertEquals(2, new ConnectionDetailsFactories(loader).getConnectionDetails("ok", true).size());
			assertEquals(Arrays.asList("good", "other"), calls);
		}
	}

	private URLClassLoader factories(Class<?>... types) throws IOException {
		Path file = directory.resolve("META-INF/spring.factories");
		Files.createDirectories(file.getParent());
		String value = ConnectionDetailsFactory.class.getName() + "="
				+ Arrays.stream(types).map(Class::getName).collect(java.util.stream.Collectors.joining(","));
		Files.write(file, value.getBytes(StandardCharsets.UTF_8));
		return new IsolatedFactories(new URL[] { directory.toUri().toURL() }, getClass().getClassLoader());
	}

	/**
	 * @author 芋道源码
	 */
	static class IsolatedFactories extends URLClassLoader {

		IsolatedFactories(URL[] urls, ClassLoader parent) {
			super(urls, parent);
		}

		@Override
		public Enumeration<URL> getResources(String name) throws IOException {
			return "META-INF/spring.factories".equals(name) ? findResources(name) : super.getResources(name);
		}

	}

	/**
	 * @author 芋道源码
	 */
	public interface Details extends ConnectionDetails {

		String value();

	}

	/**
	 * @author 芋道源码
	 */
	public interface OtherDetails extends ConnectionDetails {

		String value();

	}

	/**
	 * @author 芋道源码
	 */
	public static class Good implements ConnectionDetailsFactory<String, Details>, Ordered {

		public Details getConnectionDetails(String source) {
			calls.add("good");
			return "reject".equals(source) ? null : () -> source;
		}

		public int getOrder() {
			return -1;
		}

	}

	/**
	 * @author 芋道源码
	 */
	public static class Duplicate implements ConnectionDetailsFactory<String, Details> {

		public Details getConnectionDetails(String source) {
			return () -> source;
		}

	}

	/**
	 * @author 芋道源码
	 */
	public static class Missing implements ConnectionDetailsFactory<String, Details> {

		public Missing() {
			throw new NoClassDefFoundError("Optional SDK is absent");
		}

		public Details getConnectionDetails(String source) {
			return null;
		}

	}

	/**
	 * @author 芋道源码
	 */
	public static class Other implements ConnectionDetailsFactory<String, OtherDetails>, Ordered {

		public OtherDetails getConnectionDetails(String source) {
			calls.add("other");
			return () -> source;
		}

		public int getOrder() {
			return 1;
		}

	}

}
