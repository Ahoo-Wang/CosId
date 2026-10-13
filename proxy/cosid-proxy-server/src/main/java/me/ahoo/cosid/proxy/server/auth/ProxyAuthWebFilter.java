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
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rejects requests without a valid {@code Authorization: Bearer <token>} with {@code 401}, and requests for a namespace
 * the token, or the server, does not allow with {@code 403}.
 *
 * <p>Deny by default: every path needs the token (the proxy API, but also actuator endpoints that generate or remove ids
 * and the API docs), except the {@link ProxyAuthProperties#getPublicPaths() public paths}. Public paths are
 * {@link PathPattern}s matched the way WebFlux routes: on the decoded path without matrix parameters, so
 * {@code /%6Dachines} or {@code /actuator;x=1/health} cannot dodge or fake them.</p>
 *
 * <p>The namespace is read from the path of the machine (v1 and v2) and segment APIs, decoded the same way. Other
 * paths, such as {@code /ids} which serves the server's own namespace, need only a valid token.</p>
 *
 * @author ahoo wang
 */
@Slf4j
public class ProxyAuthWebFilter implements WebFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String NAMESPACE = "namespace";
    
    private final List<Grant> grants;
    private final List<PathPattern> publicPaths;
    @Nullable
    private final Set<String> serverNamespaces;
    /**
     * Paths without a namespace first, then the most specific ones: {@code /machines/v2/time} is not namespace
     * {@code v2}, and {@code /segments/distributor/ns/name} is not namespace {@code distributor}.
     */
    private final List<PathPattern> namespaceFreePaths;
    private final List<PathPattern> namespacedPaths;
    
    public ProxyAuthWebFilter(List<String> tokens) {
        this(tokens, ProxyAuthProperties.DEFAULT_PUBLIC_PATHS);
    }
    
    public ProxyAuthWebFilter(List<String> tokens, List<String> publicPaths) {
        this(tokens, List.of(), publicPaths, List.of());
    }
    
    /**
     * Creates the filter.
     *
     * @param tokens           tokens allowed to use every namespace
     * @param clients          tokens with the namespaces each may use
     * @param publicPaths      path patterns that need no token
     * @param serverNamespaces namespaces the server serves to anyone; empty means every namespace
     */
    public ProxyAuthWebFilter(List<String> tokens, List<ProxyAuthProperties.Client> clients, List<String> publicPaths, List<String> serverNamespaces) {
        PathPatternParser parser = new PathPatternParser();
        this.publicPaths = publicPaths.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(path -> !path.isEmpty())
            .map(parser::parse)
            .toList();
        List<Grant> allGrants = new ArrayList<>();
        tokens.stream()
            .map(Grant::of)
            .filter(Objects::nonNull)
            .forEach(allGrants::add);
        clients.stream()
            .filter(Objects::nonNull)
            .map(client -> Grant.of(client.getToken(), client.getNamespaces()))
            .filter(Objects::nonNull)
            .forEach(allGrants::add);
        this.grants = List.copyOf(allGrants);
        Set<String> namespaces = names(serverNamespaces);
        this.serverNamespaces = namespaces.isEmpty() ? null : namespaces;
        this.namespaceFreePaths = List.of(parser.parse("/machines/v2/time"));
        this.namespacedPaths = List.of(
            parser.parse("/machines/v2/{namespace}/{*rest}"),
            parser.parse("/machines/{namespace}"),
            parser.parse("/machines/{namespace}/{*rest}"),
            parser.parse("/segments/distributor/{namespace}/{name}"),
            parser.parse("/segments/{namespace}/{name}"));
        if (this.grants.isEmpty() && log.isWarnEnabled()) {
            log.warn("No [{}.tokens] or [{}.clients] configured: the CosId proxy server is open to anyone who can reach it.",
                ProxyAuthProperties.PREFIX, ProxyAuthProperties.PREFIX);
        }
    }
    
    private static Set<String> names(@Nullable List<String> namespaces) {
        if (namespaces == null) {
            return Set.of();
        }
        return namespaces.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(namespace -> !namespace.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }
    
    public boolean isEnabled() {
        return !grants.isEmpty();
    }
    
    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }
        List<Grant> presented = List.of();
        if (isEnabled()) {
            presented = authorize(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            if (presented.isEmpty()) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                return exchange.getResponse().setComplete();
            }
        }
        String namespace = namespaceOf(path);
        if (namespace != null && !isAllowed(presented, namespace)) {
            if (log.isInfoEnabled()) {
                log.info("Rejected a request for namespace:[{}] at [{}]: not allowed for this token or server.", namespace, path.value());
            }
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
    
    public boolean isPublic(PathContainer path) {
        for (PathPattern publicPath : publicPaths) {
            if (publicPath.matches(path)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * The namespace a request is for.
     *
     * @param path the path within the application
     * @return the namespace, or {@code null} for a path without one
     */
    @Nullable
    String namespaceOf(PathContainer path) {
        for (PathPattern namespaceFreePath : namespaceFreePaths) {
            if (namespaceFreePath.matches(path)) {
                return null;
            }
        }
        for (PathPattern namespacedPath : namespacedPaths) {
            PathPattern.PathMatchInfo matchInfo = namespacedPath.matchAndExtract(path);
            if (matchInfo != null) {
                return matchInfo.getUriVariables().get(NAMESPACE);
            }
        }
        return null;
    }
    
    private boolean isAllowed(List<Grant> presented, String namespace) {
        if (serverNamespaces != null && !serverNamespaces.contains(namespace)) {
            return false;
        }
        if (!isEnabled()) {
            return true;
        }
        for (Grant grant : presented) {
            if (grant.allows(namespace)) {
                return true;
            }
        }
        return false;
    }
    
    boolean isAuthorized(String authorization) {
        return !authorize(authorization).isEmpty();
    }
    
    private List<Grant> authorize(@Nullable String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return List.of();
        }
        byte[] presented = authorization.substring(BEARER_PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8);
        List<Grant> matched = new ArrayList<>(1);
        // MessageDigest.isEqual does not stop at the first differing byte, and every token is compared.
        for (Grant grant : grants) {
            if (MessageDigest.isEqual(grant.token, presented)) {
                matched.add(grant);
            }
        }
        return matched;
    }
    
    /**
     * A token and the namespaces it may use, {@code null} for every namespace.
     */
    private record Grant(byte[] token, @Nullable Set<String> namespaces) {
        @Nullable
        static Grant of(@Nullable String token) {
            return of(token, null);
        }
        
        @Nullable
        static Grant of(@Nullable String token, @Nullable List<String> namespaces) {
            if (token == null || token.isBlank()) {
                return null;
            }
            Set<String> names = names(namespaces);
            return new Grant(token.trim().getBytes(StandardCharsets.UTF_8), names.isEmpty() ? null : names);
        }
        
        boolean allows(String namespace) {
            return namespaces == null || namespaces.contains(namespace);
        }
    }
}
