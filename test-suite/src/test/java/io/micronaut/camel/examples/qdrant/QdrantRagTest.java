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

import dev.langchain4j.model.chat.ChatModel;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.qdrant.QdrantContainer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports the {@code qdrant} example of camel-spring-boot-examples.
 * <p>
 * The example uses the {@code openai} component with Ollama's OpenAI API for the embeddings and the chat
 * completions. The port uses the {@code langchain4j-embeddings} and {@code langchain4j-chat} components, to
 * which Camel autowires the {@code EmbeddingModel} and {@code ChatModel} beans of the application, as an
 * application gets them from micronaut-langchain4j (for example from {@code langchain4j.ollama.*}). Running
 * Ollama with the example's models in the tests is not practical (an image of several gigabytes), so the
 * test has a bag-of-words embedding model and a scripted chat model, and checks the retrieval: Qdrant, run
 * with Testcontainers, returns the titles with the word "moon" first. The search results are written as
 * JSON with the {@code micronaut-json} data format instead of camel-jackson, and the input is a part of the
 * example's playlist. The example has no test.
 */
@MicronautTest(environments = "example-qdrant")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QdrantRagTest implements TestPropertyProvider {

    static final QdrantContainer QDRANT = new QdrantContainer("qdrant/qdrant:v1.18.3");

    @Inject
    ChatModel chatModel;

    @Inject
    JsonMapper jsonMapper;

    @Override
    public Map<String, String> getProperties() {
        QDRANT.start();
        return Map.of(
            "camel.component.qdrant.host", QDRANT.getHost(),
            "camel.component.qdrant.port", String.valueOf(QDRANT.getGrpcPort()));
    }

    @AfterAll
    void stopQdrant() {
        QDRANT.stop();
    }

    @Test
    void theSongsAboutTheMoonAreFound() throws Exception {
        String prompt = awaitRagPrompt();

        String results = prompt.substring(prompt.indexOf('\n') + 1, prompt.indexOf("\n\n"));
        List<Map<String, Object>> ranked = jsonMapper.readValue(results, List.class);
        assertEquals(5, ranked.size(), "the number of results that the chat model extracted");
        List<Object> titles = ranked.stream().limit(3).map(result -> result.get("content")).toList();
        assertTrue(titles.containsAll(List.of(
            "The Police - Walking on the Moon",
            "Creedence Clearwater Revival - Bad Moon Rising",
            "Pink Floyd - Brain Damage (Dark Side of the Moon)")), "ranked first: " + titles);
    }

    private String awaitRagPrompt() throws InterruptedException {
        ScriptedChatModel model = (ScriptedChatModel) chatModel;
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<String> prompt = model.prompts.stream().filter(p -> p.startsWith("Based on the following search results")).findFirst();
            if (prompt.isPresent()) {
                return prompt.get();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("The RAG pipeline did not run: " + model.prompts);
    }
}
