/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosid.server;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * {@code cosid.server.*} (08-remote.md): the backing store, the limits, the blocking pool, the authentication and the
 * optional id endpoints.
 *
 * @param store    the name of the backing store among {@code cosid.stores}; may be left out when there is only one
 * @param limits   what a request may ask for
 * @param blocking the bounded pool the store calls run on
 * @param auth     the bearer token authentication, on by default
 * @param ids      the optional id endpoints
 * @param docs     whether the OpenAPI document and Swagger UI need a token
 */
@ConfigurationProperties(prefix = ServerProperties.PREFIX)
public record ServerProperties(String store,
                               @DefaultValue Limits limits,
                               @DefaultValue Blocking blocking,
                               @DefaultValue Auth auth,
                               @DefaultValue Ids ids,
                               @DefaultValue Docs docs) {
    /** The prefix of the properties. */
    public static final String PREFIX = "cosid.server";

    /**
     * What one request may ask for (08 §2). Exceeding a limit is a {@code 400}, except the key cap ({@code 429}).
     *
     * @param minTtl              the shortest lease ttl
     * @param maxTtl              the longest lease ttl
     * @param maxTolerance        the largest tolerance (D20)
     * @param maxSlotLimit        the largest slot limit of a namespace; the stores prepare that many slots
     * @param maxReserve          the largest range one reserve takes
     * @param maxCount            the most ids one id request takes
     * @param maxKeysPerNamespace     the most range keys one client may use in one namespace on this server instance; 0 for
     *                                no cap
     * @param maxNamespacesPerClient  the most namespaces one client may use range keys in on this server instance; 0 for no
     *                                cap (a key without {@code /} is a namespace of its own)
     * @param keyIdleTimeout          a range key unused this long no longer counts; zero to keep every key
     */
    public record Limits(@DefaultValue("100ms") Duration minTtl,
                         @DefaultValue("1h") Duration maxTtl,
                         @DefaultValue("1m") Duration maxTolerance,
                         @DefaultValue("65536") int maxSlotLimit,
                         @DefaultValue("1000000000000") long maxReserve,
                         @DefaultValue("1000") int maxCount,
                         @DefaultValue("10000") int maxKeysPerNamespace,
                         @DefaultValue("1000") int maxNamespacesPerClient,
                         @DefaultValue("1h") Duration keyIdleTimeout) {
        public Limits {
            Objects.requireNonNull(minTtl, "min-ttl");
            Objects.requireNonNull(maxTtl, "max-ttl");
            Objects.requireNonNull(maxTolerance, "max-tolerance");
            if (minTtl.isNegative() || minTtl.isZero() || maxTtl.compareTo(minTtl) < 0) {
                throw new IllegalArgumentException(PREFIX + ".limits: need 0 < min-ttl <= max-ttl, got " + minTtl + " and " + maxTtl);
            }
            if (maxTolerance.isNegative()) {
                throw new IllegalArgumentException(PREFIX + ".limits.max-tolerance must not be negative: " + maxTolerance);
            }
            positive(maxSlotLimit, "max-slot-limit");
            positive(maxReserve, "max-reserve");
            positive(maxCount, "max-count");
            if (maxKeysPerNamespace < 0) {
                throw new IllegalArgumentException(PREFIX + ".limits.max-keys-per-namespace must not be negative: " + maxKeysPerNamespace);
            }
            if (maxNamespacesPerClient < 0) {
                throw new IllegalArgumentException(PREFIX + ".limits.max-namespaces-per-client must not be negative: " + maxNamespacesPerClient);
            }
            Objects.requireNonNull(keyIdleTimeout, "key-idle-timeout");
            if (keyIdleTimeout.isNegative()) {
                throw new IllegalArgumentException(PREFIX + ".limits.key-idle-timeout must not be negative: " + keyIdleTimeout);
            }
        }
    }

    /**
     * The bounded pool the blocking store calls run on (08 §2): when its threads are busy and its queue is full, a call
     * gets {@code 503} with {@code Retry-After: 1}.
     *
     * @param threads       the threads
     * @param queueCapacity the calls that may wait
     */
    public record Blocking(@DefaultValue("64") int threads, @DefaultValue("1024") int queueCapacity) {
        public Blocking {
            positive(threads, "blocking.threads");
            if (queueCapacity < 0) {
                throw new IllegalArgumentException(PREFIX + ".blocking.queue-capacity must not be negative: " + queueCapacity);
            }
        }
    }

    /**
     * Bearer token authentication, on by default (08 §3): without a token or a client the server refuses to start,
     * unless {@code allow-anonymous} is set.
     *
     * @param allowAnonymous allow every call without a token, when no token and no client is configured
     * @param tokens         tokens that may use every namespace and key
     * @param clients        tokens limited to namespaces and key prefixes
     * @param publicPaths    path patterns that need no token
     */
    public record Auth(boolean allowAnonymous,
                       @DefaultValue List<String> tokens,
                       @DefaultValue List<Client> clients,
                       @DefaultValue({"/actuator/health", "/actuator/health/**"}) List<String> publicPaths) {
        public Auth {
            tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
            clients = List.copyOf(Objects.requireNonNull(clients, "clients"));
            publicPaths = List.copyOf(Objects.requireNonNull(publicPaths, "publicPaths"));
        }
    }

    /**
     * A client token and what it may use: a slot namespace, a range key whose first segment is the namespace, or a
     * range key starting with a prefix. {@code *} as a namespace allows every namespace. The id endpoints need the
     * namespace of the server's runtime ({@code cosid.namespace}).
     *
     * @param name        the client, for logs
     * @param token       the token
     * @param namespaces  the namespaces
     * @param keyPrefixes the range key prefixes
     */
    public record Client(String name, String token, @DefaultValue List<String> namespaces, @DefaultValue List<String> keyPrefixes) {
        public Client {
            namespaces = List.copyOf(Objects.requireNonNull(namespaces, "namespaces"));
            keyPrefixes = List.copyOf(Objects.requireNonNull(keyPrefixes, "keyPrefixes"));
        }
    }

    /**
     * The optional id endpoints {@code /ids/**}, served by the generators of the server's {@code CosIdRuntime}.
     *
     * @param enabled whether they are on; off by default
     */
    public record Ids(boolean enabled) {
    }

    /**
     * The OpenAPI document and Swagger UI (springdoc). By default they need an unrestricted token; {@code public} adds
     * their read-only paths ({@link #paths}) to {@code auth.public-paths}, so anyone, with or without a token, can read
     * them in a browser. The calls "Try it out" makes still need a token (Authorize), checked as any other call.
     *
     * @param publicAccess {@code cosid.server.docs.public}: whether the documentation needs no token; off by default
     */
    public record Docs(@Name("public") boolean publicAccess) {
        /**
         * The springdoc paths {@code public} opens: the UI, its resources and configuration, the document in JSON and
         * YAML.
         *
         * @param apiDocsPath   {@code springdoc.api-docs.path}
         * @param swaggerUiPath {@code springdoc.swagger-ui.path}
         * @return the path patterns
         */
        public static List<String> paths(String apiDocsPath, String swaggerUiPath) {
            return List.of(swaggerUiPath, "/swagger-ui/**", apiDocsPath, apiDocsPath + "/**", apiDocsPath + ".yaml");
        }
    }

    private static void positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(PREFIX + "." + name + " must be positive: " + value);
        }
    }
}
