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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.ai.observation.AiOperationMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavioral contracts for records and platform operations on the actual Java 8 VM.
 *
 * @author 芋道源码
 */
class Java8CompatibilityTests {

	@Test
	void executesOnJava8() {
		assertThat(System.getProperty("java.specification.version")).isEqualTo("1.8");
	}

	@Test
	void preservesRecordConstructorValidationAndAccessors() {
		assertThatIllegalArgumentException().isThrownBy(() -> new AiOperationMetadata("", "provider"));
		AiOperationMetadata metadata = new AiOperationMetadata("chat", "provider");
		assertThat(metadata.operationType()).isEqualTo("chat");
		assertThat(metadata.toString()).isEqualTo("AiOperationMetadata[operationType=chat, provider=provider]");
		assertThat(metadata).isEqualTo(new AiOperationMetadata("chat", "provider"));
	}

	@Test
	void roundTripsRenamedGenericJsonComponents() throws Exception {
		ObjectMapper mapper = JacksonUtils.getDefaultJsonMapper();
		Payload<List<String>> payload = new Payload<>(Arrays.asList("a", "b"), "hello");
		String json = mapper.writeValueAsString(payload);
		assertThat(mapper.readTree(json).path("wire_name").asText()).isEqualTo("hello");
		assertThat(mapper.readTree(json).has("name")).isFalse();
		Payload<?> restored = mapper.readValue(json, Payload.class);
		assertThat(restored.value()).isEqualTo(payload.value());
		assertThat(restored.name()).isEqualTo(payload.name());
	}

	@Test
	void retainsRecordFloatingPointAndArrayIdentitySemantics() {
		byte[] array = { 1, 2 };
		Metrics a = new Metrics(Double.NaN, -0.0f, true, array);
		Metrics b = new Metrics(Double.NaN, -0.0f, true, array);
		assertThat(a).isEqualTo(b);
		assertThat(a.hashCode()).isEqualTo(b.hashCode());
		assertThat(a).isNotEqualTo(new Metrics(Double.NaN, 0.0f, true, array));
		assertThat(a).isNotEqualTo(new Metrics(Double.NaN, -0.0f, true, array.clone()));
		int expected = 31 * (31 * (31 * Double.hashCode(Double.NaN) + Float.hashCode(-0.0f)) + Boolean.hashCode(true))
				+ array.hashCode();
		assertThat(a.hashCode()).isEqualTo(expected);
	}

	@Test
	void immutableFactoriesRejectNullsAndDuplicatesAndCopyInputs() {
		List<String> mutable = new ArrayList<>(Arrays.asList("one"));
		List<String> copy = List.copyOf(mutable);
		mutable.add("two");
		assertThat(copy).containsExactly("one");
		assertThatThrownBy(() -> copy.add("three")).isInstanceOf(UnsupportedOperationException.class);
		assertThatNullPointerException().isThrownBy(() -> List.of("a", null));
		assertThatIllegalArgumentException().isThrownBy(() -> java.util.Set.of("a", "a"));
		assertThat(java.util.Set.copyOf(Arrays.asList("a", "a"))).containsExactly("a");
		assertThatIllegalArgumentException().isThrownBy(() -> Map.of("k", 1, "k", 2));
	}

	@Test
	void streamToListRemainsImmutableAndPermitsNull() {
		List<String> result = Arrays.asList("one", null).stream().toList();
		assertThat(result).containsExactly("one", null);
		assertThatThrownBy(() -> result.set(0, "two")).isInstanceOf(UnsupportedOperationException.class);
		assertThat(Java8Platform.isBlank("\u2003\t")).isTrue();
		assertThat(Java8Platform.isBlank("\u00a0")).isFalse();
	}

	@Test
	void propagatesJsonAnnotationsOnlyToTheirLegalTargets() throws Exception {
		ObjectMapper mapper = JacksonUtils.getDefaultJsonMapper();
		ValuePayload value = ValuePayload.of("plain");
		assertThat(mapper.writeValueAsString(value)).isEqualTo("\"plain\"");
		assertThat(mapper.readValue("\"decoded\"", ValuePayload.class).value()).isEqualTo("decoded");
		HiddenPayload hidden = new HiddenPayload("visible", "secret");
		assertThat(mapper.writeValueAsString(hidden)).isEqualTo("{\"visible\":\"visible\"}");
		HiddenPayload restored = mapper.readValue("{\"visible\":\"restored\",\"secret\":\"ignored\"}",
				HiddenPayload.class);
		assertThat(restored.visible()).isEqualTo("restored");
		assertThat(restored.secret()).isNull();
	}

	@Test
	void preservesAccessOnlyJsonPropertyNamesWithoutParameterNamesModule() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		VisibilityPayload payload = new VisibilityPayload("secret", "visible");
		assertThat(mapper.writeValueAsString(payload)).isEqualTo("{\"renamed\":\"visible\"}");
		VisibilityPayload restored = mapper.readValue("{\"secret\":\"token\",\"renamed\":\"ok\"}",
				VisibilityPayload.class);
		assertThat(restored.secret()).isEqualTo("token");
		assertThat(restored.value()).isEqualTo("ok");
	}

	@Test
	void interfaceMemberRecordKeepsItsPublicCanonicalConstructor() throws Exception {
		assertThat(java.lang.reflect.Modifier
			.isPublic(PublicContract.Value.class.getDeclaredConstructors()[0].getModifiers())).isTrue();
	}

	@Test
	void preservesCommentedCollectionFactoriesAndPrivateStaticInterfaceCalls() {
		Map<String, Integer> values = // retain the upstream formatting comment
				Map.of("value", InterfaceFactory.value());
		assertThat(values).containsEntry("value", 8);
	}

	@Test
	void optionalFallbackIsLazyAndRequiresNonNullSupplierAndResult() {
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		java.util.Optional<String> present = java.util.Optional.of("present");
		assertThat(Java8Platform.optionalOr(present, () -> {
			calls.incrementAndGet();
			return java.util.Optional.of("other");
		})).isSameAs(present);
		assertThat(calls).hasValue(0);
		assertThat(Java8Platform.optionalOr(java.util.Optional.empty(), () -> java.util.Optional.of("fallback")))
			.hasValue("fallback");
		assertThatNullPointerException().isThrownBy(() -> Java8Platform.optionalOr(present, null));
		assertThatNullPointerException()
			.isThrownBy(() -> Java8Platform.optionalOr(java.util.Optional.empty(), () -> null));
	}

	@Test
	void classifiesSpring6SimpleValuesOnSpring5() {
		for (Class<?> type : new Class<?>[] { String.class, int.class, java.util.UUID.class, java.nio.file.Path.class,
				java.time.ZoneId.class, java.util.regex.Pattern.class, java.nio.charset.Charset.class,
				java.util.Currency.class, java.net.InetAddress.class }) {
			assertThat(Java8SpringTypes.isSimpleValueType(type)).as(type.getName()).isTrue();
		}
		assertThat(Java8SpringTypes.isSimpleValueType(Void.class)).isFalse();
		assertThat(Java8SpringTypes.isSimpleValueType(void.class)).isFalse();
		assertThat(Java8SpringTypes.isSimpleValueType(Metrics.class)).isFalse();
	}

	@Test
	void limitsStreamReadsAndSplitsOnlyJavaLineTerminators() throws Exception {
		java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(new byte[] { 1, 2, 3 });
		assertThat(Java8Platform.readNBytes(input, 2)).containsExactly(1, 2);
		assertThat(input.read()).isEqualTo(3);
		assertThat(Java8Platform.readNBytes(new java.io.ByteArrayInputStream(new byte[] { 4, 5 }), Integer.MAX_VALUE))
			.containsExactly(4, 5);
		assertThatIllegalArgumentException().isThrownBy(() -> Java8Platform.readNBytes(input, -1));
		try (java.util.stream.Stream<String> lines = Java8Platform.stringLines("one\r\ntwo\rthree\n")) {
			assertThat(lines).containsExactly("one", "two", "three");
		}
	}

	@Test
	void keepsFactoryInferenceAndTypedCollectionStreamApis() {
		var values = Map.of("one", 1, "two", 2);
		String key = values.keySet().iterator().next();
		assertThat(key).isNotEmpty();
		List<String> names = List.of("one", "two");
		assertThat(names.toArray(String[]::new)).containsExactly("one", "two");
		assertThat(names.stream().toArray(String[]::new)).containsExactly("one", "two");
		assertThat(java.util.Optional.of("value").stream()).containsExactly("value");
		assertThat(java.util.Optional.empty().stream()).isEmpty();
		String text = "hi";
		assertThat(text.repeat(2)).isEqualTo("hihi");
	}

	/**
	 * @author 芋道源码
	 */
	private record ValuePayload(@com.fasterxml.jackson.annotation.JsonValue Object value) {

		@com.fasterxml.jackson.annotation.JsonCreator
		public static ValuePayload of(Object value) {
			return new ValuePayload(value);
		}
	}

	/**
	 * @author 芋道源码
	 */
	private record VisibilityPayload(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String secret,
			@JsonProperty("renamed") String value) {
	}

	/**
	 * @author 芋道源码
	 */
	private record HiddenPayload(String visible, @com.fasterxml.jackson.annotation.JsonIgnore String secret) {
	}

	/**
	 * @author 芋道源码
	 */
	private interface PublicContract {

		/**
		 * @author 芋道源码
		 */
		record Value(String text) {
		}

	}

	/**
	 * @author 芋道源码
	 */
	private record Payload<T>(T value, @JsonProperty("wire_name") String name) {
	}

	/**
	 * @author 芋道源码
	 */
	private record Metrics(double score, float zero, boolean valid, byte[] bytes) {
	}

	/**
	 * @author 芋道源码
	 */
	private interface InterfaceFactory {

		static int value() {
			return privateValue();
		}

		private static int privateValue() {
			return 8;
		}

	}

}
