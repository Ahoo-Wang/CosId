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

import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.store.remote.RemoteStore;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A cosid-server started in process on a random port. {@link #memory(Store, String...)} backs it with a store of the
 * test, through a {@code memory} {@link StoreProvider} bean, the same path as any store type.
 */
final class TestServer implements AutoCloseable {
    static final String TOKEN = "test-token-0123456789";

    private final ConfigurableApplicationContext context;
    private final URI baseUrl;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private TestServer(ConfigurableApplicationContext context) {
        this.context = context;
        this.baseUrl = URI.create("http://localhost:" + context.getEnvironment().getProperty("local.server.port"));
    }

    /**
     * Starts a server on the store, with the token {@link #TOKEN} unless the properties configure authentication.
     */
    /** The limits the store contracts need: 1ms ttls and reserves up to {@code Long.MAX_VALUE}. */
    static final String[] CONTRACT_LIMITS = {"cosid.server.limits.min-ttl=1ms", "cosid.server.limits.max-reserve=" + Long.MAX_VALUE};

    static TestServer memory(Store store, String... properties) {
        List<String> all = new ArrayList<>(List.of("cosid.stores.mem.type=memory"));
        all.addAll(List.of(properties));
        return start(store, all.toArray(String[]::new));
    }

    static TestServer start(Store store, String... properties) {
        List<String> all = new ArrayList<>(List.of("server.port=0", "spring.main.banner-mode=off", "logging.level.root=warn"));
        if (List.of(properties).stream().noneMatch(property -> property.startsWith("cosid.server.auth."))) {
            all.add("cosid.server.auth.tokens[0]=" + TOKEN);
        }
        all.addAll(List.of(properties));
        // As command line arguments: they win over application.yaml (server.port).
        SpringApplicationBuilder builder = new SpringApplicationBuilder(CosIdServer.class);
        if (store != null) {
            builder.initializers(context -> context.getBeanFactory().registerSingleton("memoryStoreProvider", new MemoryProvider(store)));
        }
        return new TestServer(builder.run(all.stream().map(property -> "--" + property).toArray(String[]::new)));
    }

    URI baseUrl() {
        return baseUrl;
    }

    ConfigurableApplicationContext context() {
        return context;
    }

    RemoteStore client() {
        return client(TOKEN);
    }

    RemoteStore client(String token) {
        return new RemoteStore(baseUrl, Optional.ofNullable(token), Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    HttpResponse<String> post(String path, String json, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUrl.resolve(path)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return send(request.build());
    }

    HttpResponse<String> get(String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUrl.resolve(path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return send(request.build());
    }

    HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    HttpClient http() {
        return http;
    }

    @Override
    public void close() {
        http.close();
        context.close();
    }

    /** How many stores the {@code memory} provider opened. */
    int memoryOpens() {
        return context.getBean("memoryStoreProvider", MemoryProvider.class).opens().get();
    }

    /** Opens the test's store for type {@code memory}, counting; closing it is the test's business. */
    record MemoryProvider(Store store, AtomicInteger opens) implements StoreProvider {
        MemoryProvider(Store store) {
            this(store, new AtomicInteger());
        }

        @Override
        public String type() {
            return "memory";
        }

        @Override
        public Store open(StoreConfig config) {
            opens.incrementAndGet();
            return new Store() {
                @Override
                public me.ahoo.cosid.segment.RangeStore ranges() {
                    return store.ranges();
                }

                @Override
                public me.ahoo.cosid.lease.SlotLeaseStore slots() {
                    return store.slots();
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
