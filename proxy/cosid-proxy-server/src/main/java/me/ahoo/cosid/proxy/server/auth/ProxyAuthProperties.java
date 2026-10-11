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
 * Authentication of the CosId proxy API ({@code /machines}, {@code /segments}, {@code /ids}).
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
 * Without any token the API is open, as before, and a warning is logged at startup.</p>
 *
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = ProxyAuthProperties.PREFIX)
public class ProxyAuthProperties {
    public static final String PREFIX = "cosid.proxy.server.auth";
    
    /**
     * Accepted bearer tokens. Empty means no authentication.
     */
    private List<String> tokens = new ArrayList<>();
    
    public List<String> getTokens() {
        return tokens;
    }
    
    public void setTokens(List<String> tokens) {
        this.tokens = tokens;
    }
}
