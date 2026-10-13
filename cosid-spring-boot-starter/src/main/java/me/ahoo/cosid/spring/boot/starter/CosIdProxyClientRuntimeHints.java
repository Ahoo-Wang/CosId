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

import me.ahoo.cosid.proxy.api.MachineLeaseClient;
import me.ahoo.cosid.proxy.api.SegmentClient;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Hints for the plain proxy clients ({@code cosid.proxy.client=http-interface}): {@code HttpServiceProxyFactory} creates
 * them as JDK proxies and reads the {@code @HttpExchange} methods of their interfaces.
 *
 * @author ahoo wang
 * @since 4.1.0
 */
public class CosIdProxyClientRuntimeHints implements RuntimeHintsRegistrar {
    
    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (Class<?> clientType : new Class<?>[] {MachineLeaseClient.class, SegmentClient.class}) {
            hints.proxies().registerJdkProxy(AopProxyUtils.completeJdkProxyInterfaces(clientType));
            for (Class<?> apiType = clientType; apiType != null; apiType = apiType.getInterfaces().length == 0 ? null : apiType.getInterfaces()[0]) {
                hints.reflection().registerType(apiType, MemberCategory.INVOKE_PUBLIC_METHODS);
            }
        }
    }
}
