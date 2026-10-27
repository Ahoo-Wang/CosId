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


package me.ahoo.cosid.flowable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.GeneratorDisabledException;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.store.tck.TestRuntimes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

class TextIdsTest {
    static CosIdRuntime runtime;

    @BeforeAll
    static void start() {
        runtime = TestRuntimes.start("text-ids");
    }

    @AfterAll
    static void close() {
        runtime.close();
    }

    @Test
    void textOfEveryStrategy() {
        assertThat(TextIds.of(runtime, TestRuntimes.DEFAULT).get(), matchesPattern("\\d{10,19}"));
        assertThat(TextIds.of(runtime, TestRuntimes.ORDER).get(), matchesPattern("O[0-9a-z]+"));
        assertThat(TextIds.of(runtime, TestRuntimes.TOKEN).get(), matchesPattern("[0-9A-Za-z]+"));
        assertThat(TextIds.of(runtime, TestRuntimes.BILL).get(), matchesPattern("\\d{14}"));
    }

    @Test
    void resolvesOnceAndKeepsTheName() {
        TextIds ids = TextIds.of(runtime, TestRuntimes.ORDER);
        assertThat(ids.generator(), is(TestRuntimes.ORDER));
        String first = ids.get();
        assertThat(ids.get().equals(first), is(false));
    }

    @Test
    void unknownOrDisabledFailsWhenUsed() {
        TextIds unknown = TextIds.of(runtime, "nope");
        assertThrows(NoSuchElementException.class, unknown::get);
        TextIds off = TextIds.of(runtime, TestRuntimes.OFF);
        assertThrows(GeneratorDisabledException.class, off::get);
    }
}
