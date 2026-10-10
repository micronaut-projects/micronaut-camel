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
package io.micronaut.camel.examples.arangodb;

import com.arangodb.entity.BaseDocument;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.arangodb.ArangoDbConstants;

@Singleton
@Requires(env = "example-arangodb")
public class CamelRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

      from("timer://foo?period=10000&repeatCount=1").routeId("arangodb")
                .bean(MyBeanService.class, "createDocument")
                .to("arangodb:myDatabase?operation=SAVE_DOCUMENT")
                .bean(MyBeanService.class, "readDocument")
                .setHeader(ArangoDbConstants.RESULT_CLASS_TYPE).constant(BaseDocument.class)
                .to("arangodb:myDatabase?operation=FIND_DOCUMENT_BY_KEY")
                .log("Received body: ${body}");
    }
}
