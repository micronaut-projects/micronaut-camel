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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link ChatModel} for the tests, named as the Ollama model that the Spring Boot starter of LangChain4j
 * creates in the example, that records the questions.
 */
@Singleton
@Named("ollamaChatModel")
@Requires(env = "example-genai-observability")
class FakeOllamaChatModel implements ChatModel {

    final Set<String> questions = ConcurrentHashMap.newKeySet();

    @Override
    public ChatResponse doChat(ChatRequest request) {
        questions.add(((UserMessage) request.messages().get(request.messages().size() - 1)).singleText());
        return ChatResponse.builder()
            .aiMessage(AiMessage.from("A one sentence answer."))
            .modelName("llama3.2:1b")
            .build();
    }
}
