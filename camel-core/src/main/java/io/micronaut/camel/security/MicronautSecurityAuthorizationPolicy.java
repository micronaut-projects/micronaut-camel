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
package io.micronaut.camel.security;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.security.authentication.Authentication;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.jspecify.annotations.Nullable;

import javax.security.auth.Subject;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A Camel {@link AuthorizationPolicy} that authorizes exchanges with the Micronaut Security
 * {@link Authentication}, the role of camel-spring-security's {@code SpringSecurityAuthorizationPolicy}.
 * Routes use it with {@code policy("name")} when it is a named bean.
 *
 * <p>The authentication is, in order:</p>
 * <ol>
 *     <li>the {@link Exchange#AUTHENTICATION} header: an {@link Authentication}, or a {@link Subject} with an
 *     {@code Authentication} principal;</li>
 *     <li>the user principal of the Micronaut {@link HttpRequest} that a {@code platform-http} consumer
 *     served, from the {@code CamelMicronautHttpRequest} header;</li>
 *     <li>the user principal of the HTTP request being served on the current thread, as with
 *     {@code SecurityService}.</li>
 * </ol>
 *
 * <p>Without an authentication, or without the required roles, the exchange fails with a
 * {@link CamelAuthorizationException}. Without roles, the policy only requires an authenticated user.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
public class MicronautSecurityAuthorizationPolicy implements AuthorizationPolicy {

    /**
     * The header of the Micronaut {@link HttpRequest} that micronaut-camel-http's {@code platform-http}
     * engine adds to the message.
     */
    static final String HTTP_REQUEST_HEADER = "CamelMicronautHttpRequest";

    private final Set<String> roles = new LinkedHashSet<>();
    private boolean allRolesRequired;

    /**
     * A policy that requires one of the given roles, or only an authenticated user when there is none.
     *
     * @param roles The roles
     */
    public MicronautSecurityAuthorizationPolicy(String... roles) {
        setRoles(roles);
    }

    /**
     * @return The roles, one of which the user must have, or all of them with {@link #isAllRolesRequired()}
     */
    public Set<String> getRoles() {
        return Set.copyOf(roles);
    }

    /**
     * @param roles The roles, one of which the user must have
     */
    public void setRoles(String... roles) {
        setRoles(Arrays.asList(roles));
    }

    /**
     * @param roles The roles, one of which the user must have
     */
    public void setRoles(Collection<String> roles) {
        this.roles.clear();
        for (String role : roles) {
            for (String r : role.split(",")) {
                if (!r.isBlank()) {
                    this.roles.add(r.trim());
                }
            }
        }
    }

    /**
     * @return Whether the user must have all the roles instead of one of them
     */
    public boolean isAllRolesRequired() {
        return allRolesRequired;
    }

    /**
     * @param allRolesRequired Whether the user must have all the roles instead of one of them. Defaults to {@code false}.
     */
    public void setAllRolesRequired(boolean allRolesRequired) {
        this.allRolesRequired = allRolesRequired;
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
        // nothing to prepare
    }

    @Override
    public Processor wrap(Route route, Processor processor) {
        return new AuthorizingProcessor(processor);
    }

    /**
     * Authorizes an exchange.
     *
     * @param exchange The exchange
     * @throws CamelAuthorizationException if the exchange has no authentication, or it lacks the required roles
     */
    public void authorize(Exchange exchange) throws CamelAuthorizationException {
        Authentication authentication = findAuthentication(exchange)
            .orElseThrow(() -> new CamelAuthorizationException("Cannot find an authenticated user", exchange));
        if (!isAuthorized(authentication)) {
            throw new CamelAuthorizationException("User " + authentication.getName() + " does not have the required roles " + roles, exchange);
        }
    }

    /**
     * @param authentication The authentication
     * @return Whether the authentication has the required roles
     */
    protected boolean isAuthorized(Authentication authentication) {
        if (roles.isEmpty()) {
            return true;
        }
        Collection<String> granted = authentication.getRoles();
        return allRolesRequired ? granted.containsAll(roles) : roles.stream().anyMatch(granted::contains);
    }

    /**
     * Finds the authentication of an exchange.
     *
     * @param exchange The exchange
     * @return The authentication, if any
     */
    protected Optional<Authentication> findAuthentication(Exchange exchange) {
        Object header = exchange.getMessage().getHeader(Exchange.AUTHENTICATION);
        Optional<Authentication> authentication = fromPrincipalHolder(header);
        if (authentication.isPresent()) {
            return authentication;
        }
        if (exchange.getMessage().getHeader(HTTP_REQUEST_HEADER) instanceof HttpRequest<?> request) {
            authentication = request.getUserPrincipal(Authentication.class);
            if (authentication.isPresent()) {
                return authentication;
            }
        }
        return ServerRequestContext.currentRequest().flatMap(request -> request.getUserPrincipal(Authentication.class));
    }

    private static Optional<Authentication> fromPrincipalHolder(@Nullable Object holder) {
        if (holder instanceof Authentication authentication) {
            return Optional.of(authentication);
        }
        if (holder instanceof Subject subject) {
            for (Principal principal : subject.getPrincipals()) {
                if (principal instanceof Authentication authentication) {
                    return Optional.of(authentication);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Authorizes the exchange before the wrapped processor runs.
     */
    private final class AuthorizingProcessor extends DelegateAsyncProcessor {

        AuthorizingProcessor(Processor processor) {
            super(processor);
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            try {
                authorize(exchange);
            } catch (CamelAuthorizationException e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
            return super.process(exchange, callback);
        }

        @Override
        public String toString() {
            return "MicronautSecurityAuthorization[" + getProcessor() + "]";
        }
    }
}
