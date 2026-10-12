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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;

class ProxyApiRuntimeHintsTest {
    
    @Test
    void registersTheJsonTypes() {
        RuntimeHints hints = new RuntimeHints();
        SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
            .load(RuntimeHintsRegistrar.class)
            .forEach(registrar -> registrar.registerHints(hints, getClass().getClassLoader()));
        for (Class<?> type : ProxyApiRuntimeHints.JSON_TYPES) {
            assertThat(RuntimeHintsPredicates.reflection().onType(type).withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS).test(hints), is(true));
        }
    }
}
