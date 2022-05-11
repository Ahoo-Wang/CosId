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

/**
 * CosId Properties.
 *
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = CosId.COSID)
public class CosIdProperties {
    public static final String DEFAULT_NAMESPACE = "{" + CosId.COSID + "}";
    private boolean enabled = true;
    
    private String namespace = DEFAULT_NAMESPACE;
    
    private ProxyProperties proxy = new ProxyProperties();
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public ProxyProperties getProxy() {
        return proxy;
    }
    
    public CosIdProperties setProxy(ProxyProperties proxy) {
        this.proxy = proxy;
        return this;
    }
}
