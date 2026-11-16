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

package cn.iocoder.ai;

import java.util.Collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.util.Nullness;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real Java 8 consumer, independent of the library reactor's inherited classpath.
 *
 * @author 芋道源码
 */
class Boot2ConsumerTests {

    @Test
    void runsOnActualJava8WithTheSelectedRuntimeDependencies() {
        assertEquals("1.8", System.getProperty("java.specification.version"));
        assertEquals("2.21.4", ObjectMapper.class.getPackage().getImplementationVersion());
        assertEquals("1.15.12", MeterRegistry.class.getPackage().getImplementationVersion());
    }

    @Test
    void invokesChatClientSynchronouslyAndAsAStream() {
        ChatClient client = ChatClient.builder(new ReplyModel()).build();
        assertEquals("hello", client.prompt().user("question").call().content());
        assertEquals(Collections.singletonList("hello"), client.prompt().user("question").stream().content().collectList().block());
    }

    @Test
    void retainsRuntimeNullnessAndToolSchemaRequiredFields() throws Exception {
        assertEquals(Nullness.NULLABLE, Nullness.forField(ToolArguments.class.getDeclaredField("optional")));
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode schema = mapper.readTree(JsonSchemaGenerator.generateForType(ToolArguments.class));
        assertTrue(schema.get("required").toString().contains("required"));
        assertFalse(schema.get("required").toString().contains("optional"));
    }

    @Test
    void startsBoot2ActuatorAndDiscoversCompatibilityAutoConfiguration() throws Exception {
        org.springframework.core.env.PropertySource<?> yaml = new YamlPropertySourceLoader()
            .load("smoke", new ClassPathResource("compatibility-smoke.yml")).get(0);
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
            .web(WebApplicationType.NONE).initializers(c -> c.getEnvironment().getPropertySources().addLast(yaml))
            .properties("spring.main.banner-mode=off", "logging.level.root=ERROR").run()) {
            assertEquals("working", context.getEnvironment().getProperty("compatibility.yaml"));
            assertNotNull(context.getBean(MeterRegistry.class));
            RestClient.Builder first = context.getBean(RestClient.Builder.class);
            RestClient.Builder second = context.getBean(RestClient.Builder.class);
            assertNotSame(first, second);
        }
    }

    /** @author 芋道源码 */
    static class ReplyModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(Collections.singletonList(new Generation(new AssistantMessage("hello"))));
        }
        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }
    }

    /** @author 芋道源码 */
    public static class ToolArguments {
        public String required;
        public @Nullable String optional;
    }

    /** @author 芋道源码 */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {
    }
}
