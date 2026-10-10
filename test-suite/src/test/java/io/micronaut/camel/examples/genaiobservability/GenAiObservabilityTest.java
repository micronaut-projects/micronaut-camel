/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.camel.examples.genaiobservability;

import dev.langchain4j.model.chat.ChatModel;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ports the routes of the {@code genai-observability} example of camel-spring-boot-examples.
 * <p>
 * The YAML routes are unchanged: they use the {@code ollamaChatModel} bean, which an application gets from
 * micronaut-langchain4j-ollama ({@code langchain4j.ollama.chat-model.*}). Running Ollama with
 * {@code llama3.2:1b} in the tests is not practical, so the test has a fake model of that name. The
 * observability part of the example is not ported: the {@code gen_ai.*} spans and metrics come from
 * camel-ai-observability, which is new in Camel 4.23, and the Perses and VictoriaTraces stack is outside the
 * application. The example has no test: this one checks that both routes call the model.
 */
@MicronautTest(environments = "example-genai-observability")
class GenAiObservabilityTest {

    @Inject
    @Named("ollamaChatModel")
    ChatModel chatModel;

    @Test
    void routesCallTheChatModel() throws InterruptedException {
        Set<String> questions = ((FakeOllamaChatModel) chatModel).questions;
        Set<String> expected = Set.of(
            "In one sentence, what is Apache Camel integration?",
            "In one sentence, what is an Enterprise Integration Pattern?");
        long deadline = System.currentTimeMillis() + 20_000;
        while (!questions.containsAll(expected) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }

        assertEquals(expected, questions);
    }
}
