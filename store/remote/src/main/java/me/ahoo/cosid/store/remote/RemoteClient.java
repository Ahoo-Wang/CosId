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

import me.ahoo.cosid.store.StoreTimeoutException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Posts JSON to {@code cosid-server} over the JDK {@link HttpClient}: one request per primitive call, the bearer token,
 * the request timeout.
 */
final class RemoteClient implements AutoCloseable {
    private final HttpClient http;
    private final URI baseUrl;
    private final Optional<String> authorization;
    private final Duration requestTimeout;

    RemoteClient(URI baseUrl, Optional<String> token, Duration connectTimeout, Duration requestTimeout) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        String base = baseUrl.toString();
        this.baseUrl = URI.create(base.endsWith("/") ? base.substring(0, base.length() - 1) : base);
        if (this.baseUrl.getScheme() == null || !this.baseUrl.getScheme().startsWith("http")) {
            throw new IllegalArgumentException("baseUrl must be an http or https URL: " + baseUrl);
        }
        this.authorization = Objects.requireNonNull(token, "token").filter(value -> !value.isBlank()).map(value -> "Bearer " + value.trim());
        this.requestTimeout = positive(requestTimeout, "requestTimeout");
        this.http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(positive(connectTimeout, "connectTimeout"))
            .build();
    }

    private static Duration positive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive: " + duration);
        }
        return duration;
    }

    URI baseUrl() {
        return baseUrl;
    }

    /** An answer of the server: its status and JSON body, empty when there was none. */
    record Response(int status, Map<String, Object> body) {
        String code() {
            Object code = body.get("code");
            return code == null ? null : code.toString();
        }

        RemoteStoreException error(String call) {
            Object message = body.get("message");
            return new RemoteStoreException(status, code(),
                call + " failed: HTTP " + status + (code() == null ? "" : " " + code()) + (message == null ? "" : ": " + message));
        }

        long longField(String name) {
            Object value = body.get(name);
            if (value instanceof Long number) {
                return number;
            }
            throw new RemoteStoreException(status, null, "Field [" + name + "] is not an integer in " + body);
        }

        String textField(String name) {
            Object value = body.get(name);
            if (value instanceof String text) {
                return text;
            }
            throw new RemoteStoreException(status, null, "Field [" + name + "] is not a string in " + body);
        }
    }

    Response post(String path, Map<String, ?> body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8));
        authorization.ifPresent(value -> request.header("Authorization", value));
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            String message = "POST " + path + " timed out after " + requestTimeout;
            throw new RemoteStoreException(message, new StoreTimeoutException(message, e));
        } catch (IOException e) {
            throw new RemoteStoreException("POST " + path + " to " + baseUrl + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteStoreException("POST " + path + " was interrupted", e);
        }
        String text = response.body();
        if (text == null || text.isBlank()) {
            return new Response(response.statusCode(), Map.of());
        }
        try {
            return new Response(response.statusCode(), Json.readObject(text));
        } catch (IllegalArgumentException e) {
            throw new RemoteStoreException(response.statusCode(), null, "POST " + path + " answered HTTP " + response.statusCode() + " without a JSON body: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        http.close();
    }
}
