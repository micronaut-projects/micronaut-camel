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

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A bag-of-words {@link EmbeddingModel} for the tests, instead of {@code nomic-embed-text} on Ollama: each
 * word that is not a stop word adds to one of 768 dimensions, so texts that share words are similar.
 */
class BagOfWordsEmbeddingModel implements EmbeddingModel {

    static final int DIMENSION = 768;

    private static final Set<String> STOP_WORDS = Set.of("the", "a", "an", "of", "on", "in", "me", "at", "to",
        "and", "give", "least", "five", "songs", "containing", "word", "title", "s", "t");

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        return Response.from(textSegments.stream().map(segment -> vector(segment.text())).toList());
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }

    private static Embedding vector(String text) {
        float[] vector = new float[DIMENSION];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!word.isEmpty() && !STOP_WORDS.contains(word)) {
                vector[Math.floorMod(word.hashCode(), DIMENSION)] += 1;
            }
        }
        // an empty text still needs a vector Qdrant accepts with the cosine distance
        vector[DIMENSION - 1] += 0.01f;
        return Embedding.from(vector);
    }
}
