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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Authentication of the CosId proxy server: with tokens configured, every path needs one except the public paths.
 *
 * <pre>{@code
 * cosid:
 *   proxy:
 *     server:
 *       auth:
 *         tokens:
 *           - ${COSID_PROXY_TOKEN}
 * }</pre>
 *
 * <p>Clients send {@code Authorization: Bearer <token>}. Several tokens can be configured at once to rotate them.
 * Public paths ({@code /actuator/health} by default) are path patterns that need no token, for example
 * {@code /v3/api-docs/**} and {@code /swagger-ui/**} to open the API docs.
 * Without any token the server fails to start, unless {@code allow-anonymous} is set: then the API is open to anyone
 * who can reach it, and a warning is logged at startup.</p>
 *
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = ProxyAuthProperties.PREFIX)
public class ProxyAuthProperties {
    public static final String PREFIX = "cosid.proxy.server.auth";
    public static final List<String> DEFAULT_PUBLIC_PATHS = healthPaths("/actuator");
    
    /**
     * The health endpoint and its groups under an actuator base path.
     *
     * @param actuatorBasePath {@code management.endpoints.web.base-path}
     * @return the public path patterns
     */
    public static List<String> healthPaths(String actuatorBasePath) {
        String basePath = actuatorBasePath == null ? "" : actuatorBasePath.trim();
        while (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        if (!basePath.isEmpty() && !basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        return List.of(basePath + "/health", basePath + "/health/**");
    }
    
    /**
     * Accepted bearer tokens. Empty means no authentication.
     */
    private List<String> tokens = new ArrayList<>();
    
    /**
     * Tokens limited to some namespaces, for example one per application. A request for another namespace, or for a
     * path without a namespace (actuator, API docs, {@code /ids}), gets {@code 403}. Tokens in {@link #getTokens()} may
     * use every namespace and every path.
     */
    private List<Client> clients = new ArrayList<>();
    
    /**
     * Path patterns that need no token. Not set: the health endpoint under
     * {@code management.endpoints.web.base-path} ({@code /actuator/health} and {@code /actuator/health/**} by default).
     */
    private List<String> publicPaths;
    
    /**
     * Run without any token, the API open to anyone who can reach it. Only behind a trusted network boundary: an
     * anonymous caller can release a live machine id, and two instances then generate the same ids. Default is
     * {@code false}: without tokens the server fails to start.
     */
    private boolean allowAnonymous = false;
    
    public boolean isAllowAnonymous() {
        return allowAnonymous;
    }
    
    public void setAllowAnonymous(boolean allowAnonymous) {
        this.allowAnonymous = allowAnonymous;
    }
    
    public List<String> getTokens() {
        return tokens;
    }
    
    public void setTokens(List<String> tokens) {
        this.tokens = tokens;
    }
    
    public List<String> getPublicPaths() {
        return publicPaths;
    }
    
    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
    
    public List<Client> getClients() {
        return clients;
    }
    
    public void setClients(List<Client> clients) {
        this.clients = clients;
    }
    
    /**
     * A token and the namespaces it may use.
     */
    public static class Client {
        /**
         * The bearer token of the client.
         */
        private String token;
        /**
         * Namespaces the token may use. Empty means none (a warning at startup): list a token that may use every
         * namespace in {@code tokens} instead.
         */
        private List<String> namespaces = new ArrayList<>();
        
        public Client() {
        }
        
        public Client(String token, List<String> namespaces) {
            this.token = token;
            this.namespaces = namespaces;
        }
        
        public String getToken() {
            return token;
        }
        
        public void setToken(String token) {
            this.token = token;
        }
        
        public List<String> getNamespaces() {
            return namespaces;
        }
        
        public void setNamespaces(List<String> namespaces) {
            this.namespaces = namespaces;
        }
    }
}
