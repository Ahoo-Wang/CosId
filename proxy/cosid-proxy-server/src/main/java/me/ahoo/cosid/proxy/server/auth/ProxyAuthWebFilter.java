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
import java.util.Map;
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
 * <p>The namespace is read from the path of the machine (v1 and v2) and segment APIs, decoded the same way. A token
 * limited to namespaces (or a server limited to namespaces) fails closed: it reaches only those namespaces and
 * {@code /machines/v2/time}, never the actuator, the API docs or {@code /ids}, and no segment name containing a dot,
 * since stores key a segment as {@code namespace + "." + name}.</p>
 *
 * @author ahoo wang
 */
@Slf4j
public class ProxyAuthWebFilter implements WebFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String NAMESPACE = "namespace";
    private static final String NAME = "name";
    
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
            .map(Grant::unrestricted)
            .filter(Objects::nonNull)
            .forEach(allGrants::add);
        for (ProxyAuthProperties.Client client : clients) {
            if (client == null) {
                continue;
            }
            Grant grant = Grant.restricted(client.getToken(), client.getNamespaces());
            if (grant == null) {
                continue;
            }
            if (grant.namespaces().isEmpty() && log.isWarnEnabled()) {
                log.warn("A [{}.clients] entry has no namespaces: its token can use none. List its namespaces, or move the token to [{}.tokens].",
                    ProxyAuthProperties.PREFIX, ProxyAuthProperties.PREFIX);
            }
            allGrants.add(grant);
        }
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
        Map<String, String> target = targetOf(path);
        boolean restricted = isEnabled() && presented.stream().noneMatch(Grant::isUnrestricted);
        if (target == null) {
            // Actuator, API docs and /ids are not scoped to a namespace: only for tokens that may use every namespace.
            if (restricted && !isNamespaceFree(path)) {
                return forbidden(exchange, path, null);
            }
            return chain.filter(exchange);
        }
        String namespace = target.get(NAMESPACE);
        if (namespace == null || !isAllowed(presented, namespace)) {
            return forbidden(exchange, path, namespace);
        }
        // Stores key a segment as namespace + "." + name: /segments/a/b.c would reach the counter c of namespace a.b.
        String name = target.get(NAME);
        if (name != null && name.indexOf('.') >= 0 && (restricted || serverNamespaces != null)) {
            return forbidden(exchange, path, namespace);
        }
        return chain.filter(exchange);
    }
    
    private static Mono<Void> forbidden(ServerWebExchange exchange, PathContainer path, @Nullable String namespace) {
        if (log.isInfoEnabled()) {
            log.info("Rejected a request for namespace:[{}] at [{}]: not allowed for this token or server.", namespace, path.value());
        }
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }
    
    public boolean isPublic(PathContainer path) {
        for (PathPattern publicPath : publicPaths) {
            if (publicPath.matches(path)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isNamespaceFree(PathContainer path) {
        for (PathPattern namespaceFreePath : namespaceFreePaths) {
            if (namespaceFreePath.matches(path)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * The path variables of a request for a namespace: {@code namespace}, and {@code name} for segments.
     *
     * @param path the path within the application
     * @return the variables, or {@code null} for a path without a namespace
     */
    @Nullable
    Map<String, String> targetOf(PathContainer path) {
        if (isNamespaceFree(path)) {
            return null;
        }
        for (PathPattern namespacedPath : namespacedPaths) {
            PathPattern.PathMatchInfo matchInfo = namespacedPath.matchAndExtract(path);
            if (matchInfo != null) {
                return matchInfo.getUriVariables();
            }
        }
        return null;
    }
    
    /**
     * The namespace a request is for.
     *
     * @param path the path within the application
     * @return the namespace, or {@code null} for a path without one
     */
    @Nullable
    String namespaceOf(PathContainer path) {
        Map<String, String> target = targetOf(path);
        return target == null ? null : target.get(NAMESPACE);
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
        static Grant unrestricted(@Nullable String token) {
            if (token == null || token.isBlank()) {
                return null;
            }
            return new Grant(token.trim().getBytes(StandardCharsets.UTF_8), null);
        }
        
        /**
         * A token limited to its namespaces: none when the list is empty, failing closed.
         */
        @Nullable
        static Grant restricted(@Nullable String token, @Nullable List<String> namespaces) {
            if (token == null || token.isBlank()) {
                return null;
            }
            return new Grant(token.trim().getBytes(StandardCharsets.UTF_8), names(namespaces));
        }
        
        boolean isUnrestricted() {
            return namespaces == null;
        }
        
        boolean allows(String namespace) {
            return namespaces == null || namespaces.contains(namespace);
        }
    }
}
