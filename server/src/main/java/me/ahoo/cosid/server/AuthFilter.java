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
 * or key ({@code 403}). No token or an unknown one is a {@code 401}.
 */
final class AuthFilter extends OncePerRequestFilter {
    private static final String BEARER = "Bearer ";

    private final Grants grants;
    private final List<PathPattern> publicPaths;

    AuthFilter(Grants grants, List<String> publicPaths) {
        this.grants = grants;
        PathPatternParser parser = new PathPatternParser();
        this.publicPaths = publicPaths.stream()
            .filter(path -> path != null && !path.isBlank())
            .map(path -> parser.parse(path.trim()))
            .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (isPublic(request)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<Grant> grant = grant(request);
        if (grant.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(("{\"code\":\"" + RemoteApi.UNAUTHORIZED + "\",\"message\":\"A valid bearer token is required\"}")
                .getBytes(StandardCharsets.UTF_8));
            return;
        }
        request.setAttribute(Grant.ATTRIBUTE, grant.get());
        chain.doFilter(request, response);
    }

    private boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        PathContainer container = PathContainer.parsePath(path);
        for (PathPattern pattern : publicPaths) {
            if (pattern.matches(container)) {
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
