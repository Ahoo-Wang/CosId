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
import static org.hamcrest.Matchers.startsWith;

import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.test.MockIdGenerator;

import org.axonframework.common.IdentifierFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

/**
 * CosIdIdentifierFactoryTest .
 *
 * @author ahoo wang
 */
class CosIdIdentifierFactoryTest {
    
    @Disabled
    @Test
    void generateIdentifier() {
        DefaultIdGeneratorProvider.INSTANCE.setShare(MockIdGenerator.INSTANCE);
        String id = IdentifierFactory.getInstance().generateIdentifier();
        assertThat(id, startsWith(MockIdGenerator.TEST_PREFIX));
    }
    
    @SetSystemProperty(key = ID_KEY, value = "axon")
    @Test
    void generateIdentifierWhenSetIdKey() {
        DefaultIdGeneratorProvider.INSTANCE.set("axon", MockIdGenerator.usePrefix("axon_"));
        String id = IdentifierFactory.getInstance().generateIdentifier();
        assertThat(id, startsWith("axon_"));
    }
}
