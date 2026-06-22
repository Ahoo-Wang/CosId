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

package me.ahoo.cosid.accessor.parser;

import me.ahoo.cosid.accessor.IdDefinition;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class NamedAccessorParserTest {

    private static final NamedDefinitionParser NAMED_PARSER = new NamedDefinitionParser("id");

    @SneakyThrows
    @Test
    void parseShouldCreateSharedDefinitionWhenFieldNameMatches() {
        Field idField = Entity.class.getDeclaredField("id");

        IdDefinition idDefinition = NAMED_PARSER.parse(List.of(Entity.class), idField);

        assertThat(idDefinition, not(sameInstance(IdDefinition.NOT_FOUND)));
        assertThat(idDefinition.getGeneratorName(), equalTo(IdGeneratorProvider.SHARE));
        assertThat(idDefinition.getIdField(), equalTo(idField));
        assertThat(idDefinition.getIdType(), equalTo(Long.class));
    }

    @SneakyThrows
    @Test
    void parseShouldReturnNotFoundWhenFieldNameDoesNotMatch() {
        Field nameField = Entity.class.getDeclaredField("name");

        IdDefinition idDefinition = NAMED_PARSER.parse(List.of(Entity.class), nameField);

        assertThat(idDefinition, sameInstance(IdDefinition.NOT_FOUND));
    }

    private static class Entity {
        @SuppressWarnings("unused")
        private Long id;

        @SuppressWarnings("unused")
        private String name;
    }
}
