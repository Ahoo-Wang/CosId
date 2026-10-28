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

package me.ahoo.cosid.store.remote;

import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import java.net.URI;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Opens {@link RemoteStore}s, type {@value #TYPE} (06-stores.md §1), registered with {@link java.util.ServiceLoader}.
 *
 * <p>Options (08-remote.md §4):
 * <ul>
 *     <li>{@code baseUrl}: the server, required;</li>
 *     <li>{@code token}: the bearer token, none by default;</li>
 *     <li>{@code connectTimeout}: 5s by default;</li>
 *     <li>{@code requestTimeout}: the timeout of each call, {@link StoreConfig#timeout()} by default.</li>
 * </ul>
 * Durations are a {@link Duration}, a number of milliseconds, or text: ISO-8601 ({@code PT5S}) or a number with
 * {@code ms}, {@code s}, {@code m} or {@code h}. The store takes no client.
 */
public final class RemoteStoreProvider implements StoreProvider {
    /** The store type. */
    public static final String TYPE = "remote";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Set<String> options() {
        return Set.of("baseUrl", "token", "connectTimeout", "requestTimeout");
    }

    @Override
    public Store open(StoreConfig config) {
        if (config.client().isPresent()) {
            throw new IllegalArgumentException("Store [" + config.name() + "]: the remote store takes no client");
        }
        Object baseUrl = config.options().get("baseUrl");
        if (baseUrl == null || baseUrl.toString().isBlank()) {
            throw new IllegalArgumentException("Store [" + config.name() + "]: set options.baseUrl, the cosid-server URL");
        }
        Optional<String> token = Optional.ofNullable(config.options().get("token")).map(Object::toString);
        Duration connectTimeout = duration(config, "connectTimeout", RemoteStore.DEFAULT_CONNECT_TIMEOUT);
        Duration requestTimeout = duration(config, "requestTimeout", config.timeout());
        return new RemoteStore(URI.create(baseUrl.toString().trim()), token, connectTimeout, requestTimeout);
    }

    static Duration duration(StoreConfig config, String name, Duration fallback) {
        Object value = config.options().get(name);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Duration duration) {
            return duration;
        }
        if (value instanceof Number millis) {
            return Duration.ofMillis(millis.longValue());
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        try {
            if (text.startsWith("p")) {
                return Duration.parse(text.toUpperCase(Locale.ROOT));
            }
            if (text.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(text.substring(0, text.length() - 2).trim()));
            }
            if (text.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(text.substring(0, text.length() - 1).trim()));
            }
            if (text.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(text.substring(0, text.length() - 1).trim()));
            }
            if (text.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(text.substring(0, text.length() - 1).trim()));
            }
            return Duration.ofMillis(Long.parseLong(text));
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException("Store [" + config.name() + "]: options." + name + " is not a duration: " + value, e);
        }
    }
}
