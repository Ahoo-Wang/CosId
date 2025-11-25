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

package me.ahoo.cosid.spring.boot.starter;

/**
 * Configuration properties for CosId proxy server connection.
 *
 * <p>This class defines properties for connecting to a remote CosId proxy server
 * that can provide centralized ID generation services. The proxy allows for
 * distributed ID generation across multiple applications or services.</p>
 *
 * <p>Example configuration:
 * <pre>{@code
 * cosid:
 *   proxy:
 *     host: "http://cosid-proxy.example.com:8688"
 * }</pre>
 *
 * @author ahoo wang
 */
public class ProxyProperties {
    /**
     * The proxy server host URL.
     * Default is "http://localhost:8688".
     */
    private String host = "http://localhost:8688";

    /**
     * Gets the proxy server host URL.
     *
     * @return the proxy server URL
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the proxy server host URL.
     *
     * @param host the proxy server URL to connect to
     * @return this properties instance for method chaining
     */
    public ProxyProperties setHost(String host) {
        this.host = host;
        return this;
    }

}
