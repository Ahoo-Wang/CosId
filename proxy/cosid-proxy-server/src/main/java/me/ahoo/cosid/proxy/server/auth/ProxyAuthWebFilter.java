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

package me.ahoo.cosid.proxy.server.auth;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * Rejects requests to the CosId proxy API without a valid {@code Authorization: Bearer <token>} with {@code 401}.
 * Other paths (actuator, API docs) are not affected.
 *
 * @author ahoo wang
 */
@Slf4j
public class ProxyAuthWebFilter implements WebFilter {
    public static final List<String> PROTECTED_PATH_PREFIXES = List.of("/machines", "/segments", "/ids");
    private static final String BEARER_PREFIX = "Bearer ";
    
    private final List<byte[]> tokens;
    
    public ProxyAuthWebFilter(List<String> tokens) {
        this.tokens = tokens.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(token -> !token.isEmpty())
            .map(token -> token.getBytes(StandardCharsets.UTF_8))
            .toList();
        if (this.tokens.isEmpty() && log.isWarnEnabled()) {
            log.warn("No [{}.tokens] configured: the CosId proxy API is open to anyone who can reach it.", ProxyAuthProperties.PREFIX);
        }
    }
    
    public boolean isEnabled() {
        return !tokens.isEmpty();
    }
    
    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        if (!isEnabled() || !isProtected(exchange.getRequest().getPath().pathWithinApplication().value())) {
            return chain.filter(exchange);
        }
        if (isAuthorized(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return exchange.getResponse().setComplete();
    }
    
    static boolean isProtected(String path) {
        for (String prefix : PROTECTED_PATH_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
    
    boolean isAuthorized(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }
        byte[] presented = authorization.substring(BEARER_PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8);
        boolean authorized = false;
        // MessageDigest.isEqual does not stop at the first differing byte, and every token is compared.
        for (byte[] token : tokens) {
            authorized |= MessageDigest.isEqual(token, presented);
        }
        return authorized;
    }
}
