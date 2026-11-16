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

package org.springframework.ai.test.model;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * Pinned MiniLM resources for real local inference tests. The model cache is shared by
 * test instances and verified against the upstream Git LFS object before use.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class MiniLmTestResources {

	private static final String SHA256 = "e3dde332c13808c718680e7bf74a574e7e5d06f55bd6e1527e51509dcb8206f3";

	private static final long SIZE = 90387630;

	private static final String MODEL_URL = "https://media.githubusercontent.com/media/spring-projects/spring-ai/4cce4caa9653092624b20ae1113633b3ad97d256/models/spring-ai-transformers/src/main/resources/onnx/all-MiniLM-L6-v2/model.onnx";

	private MiniLmTestResources() {
	}

	/**
	 * Returns the tokenizer bundled with the transformers module.
	 * @return the tokenizer resource
	 */
	public static Resource tokenizer() {
		return new ClassPathResource("onnx/all-MiniLM-L6-v2/tokenizer.json");
	}

	/**
	 * Returns the verified model, downloading the pinned object when absent.
	 * @return the model resource
	 * @throws IOException if downloading or verifying the model fails
	 */
	public static synchronized Resource model() throws IOException {
		Path directory = Path.of(System.getProperty("java.io.tmpdir"), "spring-ai-test-models");
		Files.createDirectories(directory);
		Path model = directory.resolve(SHA256 + ".onnx");
		if (!valid(model)) {
			Path temporary = Files.createTempFile(directory, "model-", ".part");
			try {
				URLConnection connection = URI.create(MODEL_URL).toURL().openConnection();
				connection.setConnectTimeout(20000);
				connection.setReadTimeout(60000);
				try (InputStream input = connection.getInputStream()) {
					Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
				}
				if (!valid(temporary)) {
					throw new IOException("MiniLM model does not match its upstream Git LFS object");
				}
				try {
					Files.move(temporary, model, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				}
				catch (AtomicMoveNotSupportedException ex) {
					Files.move(temporary, model, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			finally {
				Files.deleteIfExists(temporary);
			}
		}
		return new FileSystemResource(model);
	}

	private static boolean valid(Path path) throws IOException {
		if (!Files.isRegularFile(path) || Files.size(path) != SIZE) {
			return false;
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(path)) {
				byte[] buffer = new byte[8192];
				int count;
				while ((count = input.read(buffer)) != -1) {
					digest.update(buffer, 0, count);
				}
			}
			return SHA256.equals(hex(digest.digest()));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String hex(byte[] bytes) {
		char[] digits = "0123456789abcdef".toCharArray();
		char[] output = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			output[i * 2] = digits[(bytes[i] & 0xff) >>> 4];
			output[i * 2 + 1] = digits[bytes[i] & 0x0f];
		}
		return new String(output);
	}

}
