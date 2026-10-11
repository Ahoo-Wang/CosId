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
 * {@link me.ahoo.cosid.provider.IdGeneratorProvider} configuration ({@code cosid.provider.*}).
 *
 * @author ahoo wang
 */
public class ProviderProperties {

    /**
     * Whether each application context gets its own {@link me.ahoo.cosid.provider.IdGeneratorProvider},
     * cleared when the context closes. When {@code false} (default) the provider bean is the JVM-wide
     * {@link me.ahoo.cosid.provider.DefaultIdGeneratorProvider#INSTANCE}, as in previous 3.x releases.
     */
    private boolean isolated = false;

    public boolean isIsolated() {
        return isolated;
    }

    public ProviderProperties setIsolated(boolean isolated) {
        this.isolated = isolated;
        return this;
    }
}
