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
package io.micronaut.camel.examples.qdrant;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link ChatModel} for the tests, instead of {@code granite4:3b} on Ollama: it answers the question about
 * the number of results with 5, and the RAG question with the prompt it received, which it records.
 */
class ScriptedChatModel implements ChatModel {

    final List<String> prompts = new CopyOnWriteArrayList<>();

    @Override
    public ChatResponse doChat(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        String prompt = ((UserMessage) messages.get(messages.size() - 1)).singleText();
        prompts.add(prompt);
        String answer = prompt.startsWith("From the following query, extract the number") ? "5" : "You asked: " + prompt;
        return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
    }
}
