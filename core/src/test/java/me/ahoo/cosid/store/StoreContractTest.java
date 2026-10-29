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
package me.ahoo.cosid.store;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

class StoreContractTest {

    private static StoreProvider provider(Set<String> options) {
        return new StoreProvider() {
            @Override
            public String type() {
                return "test";
            }

            @Override
            public Set<String> options() {
                return options;
            }

            @Override
            public Store open(StoreConfig config) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void namespacesAreTheIntersectionOfTheBackends() {
        assertThat(StoreKeys.isNamespace("order-service.v2:eu_1"), is(true));
        assertThat(StoreKeys.isNamespace("a".repeat(StoreKeys.MAX_NAMESPACE_LENGTH)), is(true));
        assertThat(StoreKeys.isNamespace("a".repeat(StoreKeys.MAX_NAMESPACE_LENGTH + 1)), is(false));
        assertThat(StoreKeys.isNamespace(null), is(false));
        assertThat(StoreKeys.isNamespace("-x"), is(false));
        assertThat(StoreKeys.namespaceProblem("ok"), is(Optional.empty()));
        assertThat(StoreKeys.namespaceProblem("a/b").orElseThrow(), containsString("namespace [a/b] must be 1 to 100"));
    }

    @Test
    void namesAndKeysOmitWhatABackendRefuses() {
        assertThat(StoreKeys.nameProblem("订单-v2.x"), is(Optional.empty()));
        assertThat(StoreKeys.nameProblem(null).orElseThrow(), is("the name must not be empty"));
        assertThat(StoreKeys.nameProblem("").orElseThrow(), is("the name must not be empty"));
        assertThat(StoreKeys.nameProblem(".").orElseThrow(), is("the name must not be '.'"));
        assertThat(StoreKeys.nameProblem("a b").orElseThrow(), is("the name must not contain whitespace"));
        assertThat(StoreKeys.nameProblem("a\u0085").orElseThrow(), containsString("U+0085"));
        assertThat(StoreKeys.nameProblem("a￵").orElseThrow(), containsString("U+FFF5"));
        assertThat(StoreKeys.nameProblem("a😀").orElseThrow(), containsString("U+D83D"));
        assertThat(StoreKeys.keyProblem("owner key", "host:8080 a"), is(Optional.empty()));
        assertThat(StoreKeys.keyProblem("owner key", "").orElseThrow(), is("the owner key must not be empty"));
        assertThat(StoreKeys.keyProblem("owner key", " host").orElseThrow(), containsString("must not start or end with whitespace"));
        assertThat(StoreKeys.keyProblem("owner key", "host ").orElseThrow(), containsString("must not start or end with whitespace"));
        assertThat(StoreKeys.keyProblem("range key", "k".repeat(StoreKeys.MAX_KEY_LENGTH)), is(Optional.empty()));
        assertThat(StoreKeys.keyProblem("range key", "k".repeat(StoreKeys.MAX_KEY_LENGTH + 1)).orElseThrow(), containsString("longer than 255"));
    }

    @Test
    void optionsResolveToTheDeclaredSpelling() {
        StoreProvider declaring = provider(Set.of("initSchema", "retry.baseSleepTime"));
        assertThat(StoreOptions.resolve("db", declaring, Map.of("INIT_SCHEMA", "true", "retry.base-sleep-time", "1s")),
            is(Map.of("initSchema", "true", "retry.baseSleepTime", "1s")));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
            () -> StoreOptions.resolve("db", declaring, Map.of("initSchem", "true")));
        assertThat(unknown.getMessage(), is("store [db]: unknown option [initSchem] of type [test]; known: initSchema, retry.baseSleepTime"));
        IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
            () -> StoreOptions.resolve("db", provider(Set.of()), Map.of("url", "x")));
        assertThat(none.getMessage(), containsString("it takes no options"));
        IllegalArgumentException twice = assertThrows(IllegalArgumentException.class,
            () -> StoreOptions.resolve("db", declaring, Map.of("init-schema", "true", "initSchema", "false")));
        assertThat(twice.getMessage(), is("store [db]: options [init-schema] and [initSchema] are both initSchema"));
        assertThat(provider(Set.of()).options(), is(Set.of()));
    }
}
