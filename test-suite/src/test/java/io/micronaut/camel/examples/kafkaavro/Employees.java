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
package io.micronaut.camel.examples.kafkaavro;

import org.apache.avro.Schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * The schema of the employees, in place of the {@code Employee} class that the original generates with
 * the avro-maven-plugin.
 */
final class Employees {

    static final Schema SCHEMA = load();

    private Employees() {
    }

    private static Schema load() {
        try (InputStream schema = Employees.class.getResourceAsStream("/examples/kafka-avro/avro/employee.avsc")) {
            return new Schema.Parser().parse(schema);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
