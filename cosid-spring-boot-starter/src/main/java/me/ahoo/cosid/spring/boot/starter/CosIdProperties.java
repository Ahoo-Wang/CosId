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

import me.ahoo.cosid.CosId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for CosId framework.
 *
 * <p>This class defines the main configuration properties that control the behavior
 * of the CosId ID generation framework. Properties can be configured via
 * application.properties or application.yml using the prefix "cosid".</p>
 *
 * <p>Example configuration:
 * <pre>{@code
 * cosid:
 *   enabled: true
 *   namespace: myapp
 *   proxy:
 *     enabled: false
 * }</pre>
 *
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = CosId.COSID)
public class CosIdProperties {
    /**
     * Default namespace used when no specific namespace is configured.
     */
    public static final String DEFAULT_NAMESPACE = CosId.COSID;

    /**
     * Whether CosId auto-configuration is enabled.
     * Default is true.
     */
    private boolean enabled = true;

    /**
     * The namespace for ID generation.
     * Default is "cosid".
     */
    private String namespace = DEFAULT_NAMESPACE;

    /**
     * Proxy-related configuration properties.
     */
    @NestedConfigurationProperty
    private ProxyProperties proxy = new ProxyProperties();
    
    /**
     * Checks if CosId is enabled.
     *
     * @return true if CosId auto-configuration is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether CosId auto-configuration should be enabled.
     *
     * @param enabled true to enable CosId, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the configured namespace for ID generation.
     *
     * @return the namespace string
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace for ID generation.
     *
     * @param namespace the namespace string to use
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Gets the proxy configuration properties.
     *
     * @return the proxy properties
     */
    public ProxyProperties getProxy() {
        return proxy;
    }

    /**
     * Sets the proxy configuration properties.
     *
     * @param proxy the proxy properties to set
     * @return this properties instance for method chaining
     */
    public CosIdProperties setProxy(ProxyProperties proxy) {
        this.proxy = proxy;
        return this;
    }
}
