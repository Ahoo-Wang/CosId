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

import me.ahoo.cosid.store.remote.RemoteApi;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Bearer token authentication of every path but the public ones (08-remote.md §3), and {@code Cache-Control: no-store}
 * on every response. A known token puts its {@link Grant} in the request; the endpoints check it against the namespace
 * or key ({@code 403}). No token or an unknown one is a {@code 401}. The admin paths (Actuator, springdoc) issue ids,
 * describe every generator and expose metrics regardless of namespaces, so a client token, limited to its scope, gets
 * {@code 403} there: only the unrestricted {@code tokens} (or an open server) may use them.
 */
final class AuthFilter extends OncePerRequestFilter {
    private static final String BEARER = "Bearer ";

    private final Grants grants;
    private final List<PathPattern> publicPaths;
    private final List<PathPattern> adminPaths;

    AuthFilter(Grants grants, List<String> publicPaths, List<String> adminPaths) {
        this.grants = grants;
        this.publicPaths = patterns(publicPaths);
        this.adminPaths = patterns(adminPaths);
    }

    private static List<PathPattern> patterns(List<String> paths) {
        PathPatternParser parser = new PathPatternParser();
        return paths.stream()
            .filter(path -> path != null && !path.isBlank())
            .map(path -> parser.parse(path.trim()))
            .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        PathContainer path = PathContainer.parsePath(request.getRequestURI().substring(request.getContextPath().length()));
        if (matches(publicPaths, path)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<Grant> grant = grant(request);
        if (grant.isEmpty()) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            error(response, HttpServletResponse.SC_UNAUTHORIZED, RemoteApi.UNAUTHORIZED, "A valid bearer token is required");
            return;
        }
        if (!grant.get().unrestricted() && matches(adminPaths, path)) {
            error(response, HttpServletResponse.SC_FORBIDDEN, RemoteApi.FORBIDDEN, "This path needs an unrestricted token");
            return;
        }
        request.setAttribute(Grant.ATTRIBUTE, grant.get());
        chain.doFilter(request, response);
    }

    private static void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    private static boolean matches(List<PathPattern> patterns, PathContainer path) {
        for (PathPattern pattern : patterns) {
            if (pattern.matches(path)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Grant> grant(HttpServletRequest request) {
        if (grants.anonymous()) {
            return Optional.of(Grant.ANONYMOUS);
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length()) && header.length() > BEARER.length()) {
            return grants.find(header.substring(BEARER.length()));
        }
        return Optional.empty();
    }
}
