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

package me.ahoo.cosid.axon;

import static me.ahoo.cosid.axon.CosIdIdentifierFactory.ID_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.NotFoundIdGeneratorException;
import me.ahoo.cosid.test.MockIdGenerator;

import org.axonframework.common.IdentifierFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * CosIdIdentifierFactoryTest .
 *
 * @author ahoo wang
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CosIdIdentifierFactoryTest {
    
    @Test
    @Order(1)
    void generateIdentifierUsesShareGeneratorByDefault() {
        DefaultIdGeneratorProvider.INSTANCE.setShare(MockIdGenerator.INSTANCE);

        String id = IdentifierFactory.getInstance().generateIdentifier();

        assertThat(id, startsWith(MockIdGenerator.TEST_PREFIX));
    }
    
    @Test
    @Order(2)
    void generateIdentifierUsesConfiguredGeneratorName() {
        System.setProperty(ID_KEY, "axon");
        DefaultIdGeneratorProvider.INSTANCE.set("axon", MockIdGenerator.usePrefix("axon_"));

        String id = new CosIdIdentifierFactory().generateIdentifier();

        assertThat(id, startsWith("axon_"));
    }

    @Test
    @Order(6)
    void unbindingOneProviderKeepsOtherBindings() {
        DefaultIdGeneratorProvider first = new DefaultIdGeneratorProvider();
        first.setShare(MockIdGenerator.usePrefix("first_"));
        DefaultIdGeneratorProvider second = new DefaultIdGeneratorProvider();
        second.setShare(MockIdGenerator.usePrefix("second_"));
        CosIdIdentifierFactory factory = new CosIdIdentifierFactory();

        CosIdIdentifierFactory.bind(first);
        CosIdIdentifierFactory.bind(second);
        try {
            assertThat(factory.generateIdentifier(), startsWith("second_"));
            CosIdIdentifierFactory.unbind(second);
            assertThat(factory.generateIdentifier(), startsWith("first_"));
            CosIdIdentifierFactory.bind(second);
            CosIdIdentifierFactory.unbind(first);
            assertThat(factory.generateIdentifier(), startsWith("second_"));
        } finally {
            CosIdIdentifierFactory.unbind(first);
            CosIdIdentifierFactory.unbind(second);
        }
    }

    @AfterEach
    void destroy() {
        System.clearProperty(ID_KEY);
        DefaultIdGeneratorProvider.INSTANCE.clear();
    }

    @Test
    @Order(3)
    void generateIdentifierUsesExplicitProvider() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        provider.setShare(MockIdGenerator.usePrefix("explicit_"));

        String id = new CosIdIdentifierFactory(provider).generateIdentifier();

        assertThat(id, startsWith("explicit_"));
    }

    @Test
    @Order(4)
    void generateIdentifierUsesBoundProviderUntilUnbound() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        provider.setShare(MockIdGenerator.usePrefix("bound_"));
        DefaultIdGeneratorProvider.INSTANCE.setShare(MockIdGenerator.usePrefix("global_"));
        CosIdIdentifierFactory factory = new CosIdIdentifierFactory();

        CosIdIdentifierFactory.bind(provider);
        try {
            assertThat(factory.generateIdentifier(), startsWith("bound_"));
            assertThat(CosIdIdentifierFactory.unbind(new DefaultIdGeneratorProvider()), is(false));
        } finally {
            assertThat(CosIdIdentifierFactory.unbind(provider), is(true));
        }
        assertThat(factory.generateIdentifier(), startsWith("global_"));
    }

    @Test
    @Order(5)
    void generateIdentifierThrowsWhenGeneratorIsMissing() {
        CosIdIdentifierFactory factory = new CosIdIdentifierFactory("missing", new DefaultIdGeneratorProvider());

        Assertions.assertThrows(NotFoundIdGeneratorException.class, factory::generateIdentifier);
    }
}
