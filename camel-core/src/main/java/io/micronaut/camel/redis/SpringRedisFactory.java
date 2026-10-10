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
package io.micronaut.camel.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCredentialsProvider;
import io.lettuce.core.SslVerifyMode;
import io.lettuce.core.RedisURI;
import io.micronaut.camel.requires.RequiresCamelSpringRedis;
import io.micronaut.configuration.lettuce.DefaultRedisConfiguration;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.support.DeserializationFilterHelper;
import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.RedisCredentialsProviderFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

/**
 * Creates the Spring Data Redis beans that Camel's {@code spring-redis} component uses, from the Micronaut
 * Redis configuration ({@code redis.uri}), so that the component works without Spring Boot:
 *
 * <ul>
 *     <li>{@code redisConnectionFactory}: a {@link LettuceConnectionFactory} to the server of the Micronaut
 *     {@link RedisClient}, sharing its Lettuce client resources (event loops and timers);</li>
 *     <li>{@code redisTemplate}: a {@link RedisTemplate} on that connection factory, which Camel autowires
 *     into the component. Like the template that the component creates itself, it serializes keys and
 *     values with Java serialization, and deserializes them with Camel's default deserialization filter
 *     unless the JVM sets {@code jdk.serialFilter}.</li>
 * </ul>
 *
 * <p>A {@code RedisTemplate} or {@code RedisConnectionFactory} bean of the application replaces these.</p>
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Factory
@Internal
@RequiresCamelSpringRedis
@Requires(classes = {RedisTemplate.class, LettuceConnectionFactory.class, RedisClient.class, DefaultRedisConfiguration.class})
@Requires(beans = DefaultRedisConfiguration.class)
final class SpringRedisFactory {

    /**
     * @param configuration The Micronaut Redis configuration
     * @param client        The Micronaut Redis client, whose resources the connection factory shares
     * @return A started connection factory
     */
    @Singleton
    @Named("redisConnectionFactory")
    @Requires(missingBeans = RedisConnectionFactory.class)
    @Bean(preDestroy = "destroy")
    LettuceConnectionFactory redisConnectionFactory(DefaultRedisConfiguration configuration, @Primary RedisClient client) {
        RedisURI uri = configuration.getUri().orElse(configuration);
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(uri.getHost(), uri.getPort());
        standalone.setDatabase(uri.getDatabase());
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder()
            .clientResources(client.getResources());
        RedisCredentialsProvider credentials = uri.getCredentialsProvider();
        if (credentials != null) {
            builder.redisCredentialsProviderFactory(new RedisCredentialsProviderFactory() {
                @Override
                public RedisCredentialsProvider createCredentialsProvider(RedisConfiguration redisConfiguration) {
                    return credentials;
                }
            });
        }
        if (uri.getTimeout() != null) {
            builder.commandTimeout(uri.getTimeout());
        }
        if (uri.getClientName() != null) {
            builder.clientName(uri.getClientName());
        }
        if (uri.isSsl()) {
            LettuceClientConfiguration.LettuceSslClientConfigurationBuilder ssl = builder.useSsl();
            if (uri.isStartTls()) {
                ssl.startTls();
            }
            if (uri.getVerifyMode() == SslVerifyMode.NONE) {
                ssl.disablePeerVerification();
            }
        }
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(standalone, builder.build());
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        return connectionFactory;
    }

    /**
     * @param connectionFactory The connection factory
     * @return The template that Camel autowires into the {@code spring-redis} component
     */
    @Singleton
    @Named("redisTemplate")
    @Requires(missingBeans = RedisTemplate.class)
    RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setDefaultSerializer(new JdkSerializationRedisSerializer(
            new SerializingConverter(), new DeserializingConverter(SpringRedisFactory::deserialize)));
        template.afterPropertiesSet();
        return template;
    }

    private static Object deserialize(InputStream inputStream) throws IOException {
        ObjectInputStream stream = new ConfigurableObjectInputStream(inputStream, null);
        stream.setObjectInputFilter(DeserializationFilterHelper.resolveDeserializationFilter(null));
        try {
            return stream.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize object type", e);
        }
    }
}
