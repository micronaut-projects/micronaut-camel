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
package io.micronaut.camel.examples.platformhttp;

import java.util.Collection;

/**
 * Service interface for managing todos.
 */
public interface TodoService {
    Collection<Todo> findNotCompleted();

    Collection<Todo> findCompleted();

    long deleteCompleted();

    Todo findById(long id);

    void create(Todo todo);

    Todo update(Todo todo, long id);

    void deleteOne(long id);

    Collection<Todo> listAll();
}
