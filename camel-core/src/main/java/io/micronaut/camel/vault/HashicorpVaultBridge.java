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
package io.micronaut.camel.vault;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.HashicorpVaultConfigurationProperties;
import org.apache.camel.main.MainListenerSupport;

import java.net.URI;

/**
 * Configures Camel's HashiCorp Vault support, used by {@code {{hashicorp:...}}} placeholders and the
 * {@code hashicorp-vault} component, from the Micronaut Vault client configuration
 * ({@code vault.client.uri} and {@code vault.client.token}). Options set with
 * {@code camel.vault.hashicorp.*} take precedence.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Singleton
@Internal
@Requires(property = HashicorpVaultBridge.URI)
final class HashicorpVaultBridge extends MainListenerSupport {

    static final String URI = "vault.client.uri";
    static final String TOKEN = "vault.client.token";

    private final Environment environment;

    HashicorpVaultBridge(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void beforeConfigure(BaseMainSupport main) {
        HashicorpVaultConfigurationProperties hashicorp = main.configure().vault().hashicorp();
        URI uri = environment.getRequiredProperty(URI, URI.class);
        if (hashicorp.getHost() == null) {
            hashicorp.setHost(uri.getHost());
        }
        if (hashicorp.getPort() == null && uri.getPort() != -1) {
            hashicorp.setPort(String.valueOf(uri.getPort()));
        }
        if (hashicorp.getScheme() == null && uri.getScheme() != null) {
            hashicorp.setScheme(uri.getScheme());
        }
        if (hashicorp.getToken() == null) {
            environment.getProperty(TOKEN, String.class).ifPresent(hashicorp::setToken);
        }
    }
}
