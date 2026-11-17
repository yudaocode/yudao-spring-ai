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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Platform operations needed by generated Java 8 sources.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class Java8Platform {

	private Java8Platform() {
	}

	public static java.nio.file.Path writeString(java.nio.file.Path path, CharSequence text,
			java.nio.file.OpenOption... options) throws IOException {
		return writeString(path, text, java.nio.charset.StandardCharsets.UTF_8, options);
	}

	public static java.nio.file.Path writeString(java.nio.file.Path path, CharSequence text, Charset charset,
			java.nio.file.OpenOption... options) throws IOException {
		java.nio.ByteBuffer encoded = charset.newEncoder().encode(java.nio.CharBuffer.wrap(text));
		byte[] bytes = new byte[encoded.remaining()];
		encoded.get(bytes);
		return java.nio.file.Files.write(path, bytes, options);
	}

	public static String readString(java.nio.file.Path path) throws IOException {
		return readString(path, java.nio.charset.StandardCharsets.UTF_8);
	}

	public static String readString(java.nio.file.Path path, Charset charset) throws IOException {
		return charset.newDecoder().decode(java.nio.ByteBuffer.wrap(java.nio.file.Files.readAllBytes(path))).toString();
	}

	public static String strip(String value) {
		return stripTrailing(stripLeading(value));
	}

	public static String stripLeading(String value) {
		int start = 0;
		while (start < value.length()) {
			int codePoint = value.codePointAt(start);
			if (!Character.isWhitespace(codePoint)) {
				break;
			}
			start += Character.charCount(codePoint);
		}
		return value.substring(start);
	}

	public static String stripTrailing(String value) {
		int end = value.length();
		while (end > 0) {
			int codePoint = value.codePointBefore(end);
			if (!Character.isWhitespace(codePoint)) {
				break;
			}
			end -= Character.charCount(codePoint);
		}
		return value.substring(0, end);
	}

	public static String repeat(String value, int count) {
		if (count < 0) {
			throw new IllegalArgumentException("count must not be negative");
		}
		if (count == 0 || value.isEmpty()) {
			return "";
		}
		if ((long) value.length() * count > Integer.MAX_VALUE) {
			throw new OutOfMemoryError("Repeated string is too long");
		}
		StringBuilder result = new StringBuilder(value.length() * count);
		for (int i = 0; i < count; i++) {
			result.append(value);
		}
		return result.toString();
	}

	public static <T> java.util.concurrent.CompletableFuture<T> failedFuture(Throwable error) {
		java.util.concurrent.CompletableFuture<T> future = new java.util.concurrent.CompletableFuture<>();
		future.completeExceptionally(Objects.requireNonNull(error));
		return future;
	}

	public static <T> java.util.function.Predicate<T> not(java.util.function.Predicate<? super T> predicate) {
		Objects.requireNonNull(predicate);
		return value -> !predicate.test(value);
	}

	public static <T> java.util.stream.Stream<T> optionalStream(java.util.Optional<T> optional) {
		return optional.map(java.util.stream.Stream::of).orElseGet(java.util.stream.Stream::empty);
	}

	public static byte[] readAllBytes(java.io.InputStream input) throws IOException {
		java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int count;
		while ((count = input.read(buffer)) != -1) {
			output.write(buffer, 0, count);
		}
		return output.toByteArray();
	}

	public static boolean isBlank(String value) {
		return value.codePoints().allMatch(Character::isWhitespace);
	}

	public static <T> T requireNonNullElse(T value, T fallback) {
		return value != null ? value : Objects.requireNonNull(fallback);
	}

	public static <T> T requireNonNullElseGet(T value, Supplier<? extends T> supplier) {
		return value != null ? value : Objects.requireNonNull(Objects.requireNonNull(supplier).get());
	}

	@SuppressWarnings("unchecked")
	public static <T> java.util.Optional<T> optionalOr(java.util.Optional<T> value,
			java.util.function.Supplier<? extends java.util.Optional<? extends T>> fallback) {
		java.util.Objects.requireNonNull(fallback);
		if (value.isPresent()) {
			return value;
		}
		return (java.util.Optional<T>) java.util.Objects.requireNonNull(fallback.get());
	}

	public static byte[] readNBytes(java.io.InputStream input, int length) throws java.io.IOException {
		java.util.Objects.requireNonNull(input);
		if (length < 0) {
			throw new IllegalArgumentException("Length must not be negative");
		}
		java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(Math.min(length, 8192));
		byte[] bytes = new byte[Math.min(length, 8192)];
		int remaining = length;
		while (remaining > 0) {
			int count = input.read(bytes, 0, Math.min(bytes.length, remaining));
			if (count < 0) {
				break;
			}
			if (count == 0) {
				int value = input.read();
				if (value < 0) {
					break;
				}
				output.write(value);
				remaining--;
			}
			else {
				output.write(bytes, 0, count);
				remaining -= count;
			}
		}
		return output.toByteArray();
	}

	public static java.util.stream.Stream<String> stringLines(String value) {
		java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.StringReader(value));
		return reader.lines().onClose(() -> {
			try {
				reader.close();
			}
			catch (java.io.IOException error) {
				throw new java.io.UncheckedIOException(error);
			}
		});
	}

	public static String hexString(byte[] bytes) {
		char[] alphabet = "0123456789abcdef".toCharArray();
		char[] hex = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int value = bytes[i] & 255;
			hex[i * 2] = alphabet[value >>> 4];
			hex[i * 2 + 1] = alphabet[value & 15];
		}
		return new String(hex);
	}

}
