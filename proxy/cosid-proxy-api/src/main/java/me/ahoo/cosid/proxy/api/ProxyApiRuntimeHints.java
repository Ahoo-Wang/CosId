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

package me.ahoo.cosid.proxy.api;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Native image hints of the CosId proxy API: the request and response types that are read and written as JSON.
 *
 * @author ahoo wang
 */
public class ProxyApiRuntimeHints implements RuntimeHintsRegistrar {
    static final Class<?>[] JSON_TYPES = {
        ClaimRequest.class, RenewRequest.class, ReleaseRequest.class, LeaseDto.class, LeaseResponse.class,
        MachineStateResponse.class, ErrorResponse.class
    };
    
    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), JSON_TYPES);
    }
}
