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
package io.micronaut.camel.processor;

import io.micronaut.core.annotation.Internal;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;

import java.util.List;

/**
 * The kinds of Camel extension the catalog describes, mirroring
 * {@code io.micronaut.camel.annotation.CamelConfig.Kind}.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Internal
enum CamelKind {

    COMPONENT("component", "Component", "component"),
    DATAFORMAT("dataformat", "DataFormat", "data format"),
    LANGUAGE("language", "Language", "language");

    private final String path;
    private final String typeSuffix;
    private final String description;

    CamelKind(String path, String typeSuffix, String description) {
        this.path = path;
        this.typeSuffix = typeSuffix;
        this.description = description;
    }

    /**
     * @return The suffix of the generated type names, such as {@code DataFormat}
     */
    String typeSuffix() {
        return typeSuffix;
    }

    /**
     * @return A description of the kind for documentation, such as {@code data format}
     */
    String description() {
        return description;
    }

    /**
     * @param name The name of the extension
     * @return The configuration prefix, such as {@code camel.component.kafka}
     */
    String prefix(String name) {
        return "camel." + path + "." + name;
    }

    /**
     * @param name The name of the extension
     * @return The service file that Camel resolves the extension with, present when it is on the classpath
     */
    String serviceResource(String name) {
        return "classpath:META-INF/services/org/apache/camel/" + path + "/" + name;
    }

    List<String> names(CamelCatalog catalog) {
        return switch (this) {
            case COMPONENT -> catalog.findComponentNames();
            case DATAFORMAT -> catalog.findDataFormatNames();
            case LANGUAGE -> catalog.findLanguageNames();
        };
    }

    ArtifactModel<?> model(CamelCatalog catalog, String name) {
        return switch (this) {
            case COMPONENT -> catalog.componentModel(name);
            case DATAFORMAT -> catalog.dataFormatModel(name);
            case LANGUAGE -> catalog.languageModel(name);
        };
    }

    /**
     * @param model The model of the extension
     * @return The options set on the extension itself, not on its endpoints
     */
    List<? extends BaseOptionModel> options(ArtifactModel<?> model) {
        if (model instanceof ComponentModel componentModel) {
            return componentModel.getComponentOptions();
        }
        return model.getOptions().stream().filter(option -> !"id".equals(option.getName())).toList();
    }
}
