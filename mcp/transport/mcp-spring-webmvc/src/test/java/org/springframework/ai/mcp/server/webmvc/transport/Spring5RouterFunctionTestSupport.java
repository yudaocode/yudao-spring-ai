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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Runs functional MVC routes through the actual Spring 5 RouterFunctionMapping. Closes
 * each test's web context on the same test execution thread.
 *
 * @author 芋道源码
 */
public final class Spring5RouterFunctionTestSupport implements AfterEachCallback {

	private static final ThreadLocal<List<AnnotationConfigWebApplicationContext>> CONTEXTS = ThreadLocal
		.withInitial(ArrayList::new);

	public static WebTestClient.Builder bindToRouterFunction(RouterFunction<ServerResponse> router) {
		AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
		context.setServletContext(new MockServletContext());
		context.register(MvcConfiguration.class);
		context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("testRouterFunction", router));
		try {
			context.refresh();
			CONTEXTS.get().add(context);
			return MockMvcWebTestClient.bindTo(MockMvcBuilders.webAppContextSetup(context).build());
		}
		catch (RuntimeException error) {
			context.close();
			throw error;
		}
	}

	@Override
	public void afterEach(ExtensionContext ignored) {
		try {
			CONTEXTS.get().forEach(AnnotationConfigWebApplicationContext::close);
		}
		finally {
			CONTEXTS.remove();
		}
	}

	/**
	 * @author 芋道源码
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableWebMvc
	static class MvcConfiguration {

	}

}
