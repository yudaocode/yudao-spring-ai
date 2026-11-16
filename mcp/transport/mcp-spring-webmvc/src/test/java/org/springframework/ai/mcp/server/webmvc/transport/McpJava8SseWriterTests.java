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

package org.springframework.ai.mcp.server.webmvc.transport;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Spring 5 SSE writer flushes a comment without creating data.
 *
 * @author 芋道源码
 */
class McpJava8SseWriterTests {

	@Test
	void flushesHeadersAndKeepAliveWithoutCreatingAnEmptyMessage() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setAsyncSupported(true);
		MockHttpServletResponse output = new MockHttpServletResponse();
		ServerResponse response = ServerResponse.sse(builder -> {
			ReflectionTestUtils.invokeMethod(WebMvcModernServerTransport.class, "commitHeaders", builder);
			ReflectionTestUtils.invokeMethod(WebMvcModernServerTransport.class, "flushComment", builder);
			builder.complete();
		});
		response.writeTo(request, output, () -> Collections.singletonList(new StringHttpMessageConverter()));
		assertThat(output.getContentAsString()).isEqualTo(": \n\n: \n\n");
		assertThat(output.getContentAsString()).doesNotContain("data:");
	}

}
