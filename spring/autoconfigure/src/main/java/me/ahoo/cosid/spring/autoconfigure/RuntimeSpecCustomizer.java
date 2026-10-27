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

package me.ahoo.cosid.spring.autoconfigure;

import me.ahoo.cosid.runtime.RuntimeSpec;

/**
 * Changes the {@link RuntimeSpec} built from {@code cosid.*} before the runtime starts (07-runtime-and-spring.md §4.1):
 * add, remove or change generators, stores or the machine. Customizers run in {@code @Order} order; the generators they
 * add get a bean as well.
 *
 * <p>The generator beans are registered while the bean definitions are processed, so customizer beans are created
 * that early: <strong>a customizer must not depend on other beans</strong> (declare it on a {@code static} {@code @Bean}
 * method), and it must return the same spec for the same input.
 */
@FunctionalInterface
public interface RuntimeSpecCustomizer {
    /**
     * Customizes the spec.
     *
     * @param spec the spec so far
     * @return the spec to use
     */
    RuntimeSpec customize(RuntimeSpec spec);
}
