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


package me.ahoo.cosid.integrations.axon;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;

import me.ahoo.cosid.integrations.TestRuntimes;
import me.ahoo.cosid.runtime.CosIdRuntime;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Axon loads the factory through ServiceLoader; it follows the bound runtimes. */
class CosIdIdentifierFactoryTest {
    static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    static CosIdRuntime first;
    static CosIdRuntime second;

    @BeforeAll
    static void start() {
        first = TestRuntimes.start("axon-1");
        second = TestRuntimes.start("axon-2");
    }

    @AfterAll
    static void close() {
        first.close();
        second.close();
    }

    @AfterEach
    void clean() {
        System.clearProperty(CosIdIdentifierFactory.ID_KEY);
        for (CosIdIdentifierFactory.Binding bound = CosIdIdentifierFactory.current(); bound != null; bound = CosIdIdentifierFactory.current()) {
            bound.close();
        }
    }

    static String nextIdentifier() {
        return GenericEventMessage.asEventMessage("payload").getIdentifier();
    }

    @Test
    void axonLoadsTheFactory() {
        assertThat(IdentifierFactory.getInstance(), instanceOf(CosIdIdentifierFactory.class));
    }

    @Test
    void unboundFallsBackToUuids() {
        assertThat(nextIdentifier(), matchesPattern(UUID_PATTERN));
        assertThat(nextIdentifier(), matchesPattern(UUID_PATTERN));
    }

    @Test
    void boundRuntimeGivesAxonsIdentifiers() {
        try (CosIdIdentifierFactory.Binding binding = CosIdIdentifierFactory.bind(first, TestRuntimes.ORDER)) {
            assertThat(binding.runtime(), sameInstance(first));
            assertThat(binding.generator(), is(TestRuntimes.ORDER));
            assertThat(nextIdentifier(), startsWith("O"));
        }
        assertThat(nextIdentifier(), matchesPattern(UUID_PATTERN));
    }

    @Test
    void twoRuntimesUnbindOnlyTheirOwn() {
        CosIdIdentifierFactory.Binding firstBinding = CosIdIdentifierFactory.bind(first, TestRuntimes.ORDER);
        CosIdIdentifierFactory.Binding secondBinding = CosIdIdentifierFactory.bind(second, TestRuntimes.BILL);
        assertThat(CosIdIdentifierFactory.current(), sameInstance(secondBinding));
        assertThat(nextIdentifier(), matchesPattern("\\d{14}"));

        // The first runtime closes while the second still runs: the second stays bound.
        assertThat(CosIdIdentifierFactory.unbind(firstBinding), is(true));
        assertThat(CosIdIdentifierFactory.unbind(firstBinding), is(false));
        assertThat(CosIdIdentifierFactory.current(), sameInstance(secondBinding));
        assertThat(nextIdentifier(), matchesPattern("\\d{14}"));

        // Bound again, the first wins until it is unbound.
        CosIdIdentifierFactory.Binding again = CosIdIdentifierFactory.bind(first, TestRuntimes.ORDER);
        assertThat(nextIdentifier(), startsWith("O"));
        again.close();
        assertThat(nextIdentifier(), matchesPattern("\\d{14}"));
        secondBinding.close();
        assertThat(CosIdIdentifierFactory.current(), nullValue());
    }

    @Test
    void generatorFromTheSystemPropertyElseDefault() {
        try (CosIdIdentifierFactory.Binding binding = CosIdIdentifierFactory.bind(first, null)) {
            assertThat(binding.generator(), is(CosIdIdentifierFactory.DEFAULT_GENERATOR));
            assertThat(nextIdentifier(), matchesPattern("\\d+"));
        }
        System.setProperty(CosIdIdentifierFactory.ID_KEY, TestRuntimes.TOKEN);
        try (CosIdIdentifierFactory.Binding binding = CosIdIdentifierFactory.bind(first, " ")) {
            assertThat(binding.generator(), is(TestRuntimes.TOKEN));
        }
    }

    @Test
    void factoryOfOneRuntimeIgnoresTheBindings() {
        CosIdIdentifierFactory own = new CosIdIdentifierFactory(second, TestRuntimes.ORDER);
        try (CosIdIdentifierFactory.Binding binding = CosIdIdentifierFactory.bind(first, TestRuntimes.BILL)) {
            assertThat(binding.runtime(), sameInstance(first));
            assertThat(own.generateIdentifier(), startsWith("O"));
        }
        assertThat(UUID.fromString(new CosIdIdentifierFactory().generateIdentifier()).version(), is(4));
    }
}
