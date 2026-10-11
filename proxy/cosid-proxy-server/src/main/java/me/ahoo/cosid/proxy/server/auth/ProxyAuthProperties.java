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
 * Without any token the API is open, as before, and a warning is logged at startup.</p>
 *
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = ProxyAuthProperties.PREFIX)
public class ProxyAuthProperties {
    public static final String PREFIX = "cosid.proxy.server.auth";
    public static final List<String> DEFAULT_PUBLIC_PATHS = List.of("/actuator/health", "/actuator/health/**");
    
    /**
     * Accepted bearer tokens. Empty means no authentication.
     */
    private List<String> tokens = new ArrayList<>();
    
    /**
     * Path patterns that need no token.
     */
    private List<String> publicPaths = new ArrayList<>(DEFAULT_PUBLIC_PATHS);
    
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
}
