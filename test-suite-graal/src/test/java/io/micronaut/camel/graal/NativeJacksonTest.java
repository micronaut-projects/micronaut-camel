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
package io.micronaut.camel.graal;

import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The camel-jackson data format, which binds POJOs by reflection.
 */
@MicronautTest
class NativeJacksonTest {

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    void jacksonMarshalsPojos() {
        assertEquals("{\"name\":\"pen\",\"quantity\":3}", producerTemplate.requestBody("direct:jackson-marshal", new Item("pen", 3), String.class));
    }

    @Test
    void jacksonUnmarshalsPojos() {
        assertEquals(new Item("cup", 2), producerTemplate.requestBody("direct:jackson-unmarshal", "{\"name\":\"cup\",\"quantity\":2}", Item.class));
    }

    /**
     * Jackson binds the POJO by reflection.
     */
    @ReflectiveAccess
    public static class Item {
        private String name;
        private int quantity;

        public Item() {
        }

        public Item(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Item item && quantity == item.quantity && Objects.equals(name, item.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, quantity);
        }

        @Override
        public String toString() {
            return name + "=" + quantity;
        }
    }

    @Singleton
    public static class JacksonRoutes extends RouteBuilder {
        @Override
        public void configure() {
            from("direct:jackson-marshal").marshal().json(JsonLibrary.Jackson);

            from("direct:jackson-unmarshal").unmarshal().json(JsonLibrary.Jackson, Item.class);
        }
    }
}
