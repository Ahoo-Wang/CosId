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

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class CompositeFieldDefinitionParserTest {

    @SneakyThrows
    @Test
    void parseShouldReturnFirstConcreteDefinitionAndStop() {
        Field idField = Entity.class.getDeclaredField("id");
        IdDefinition expected = new IdDefinition("first", idField);
        List<String> calls = new ArrayList<>();
        FieldDefinitionParser miss = (lookupClassList, field) -> {
            calls.add("miss");
            return IdDefinition.NOT_FOUND;
        };
        FieldDefinitionParser hit = (lookupClassList, field) -> {
            calls.add("hit");
            return expected;
        };
        FieldDefinitionParser unreachable = (lookupClassList, field) -> {
            calls.add("unreachable");
            return new IdDefinition("unreachable", field);
        };
        CompositeFieldDefinitionParser parser = new CompositeFieldDefinitionParser(List.of(miss, hit, unreachable));

        IdDefinition actual = parser.parse(List.of(Entity.class), idField);

        assertThat(actual, sameInstance(expected));
        assertThat(calls, contains("miss", "hit"));
    }

    @SneakyThrows
    @Test
    void parseShouldSkipNullAndNotFoundDefinitions() {
        Field idField = Entity.class.getDeclaredField("id");
        IdDefinition expected = new IdDefinition("fallback", idField);
        CompositeFieldDefinitionParser parser = new CompositeFieldDefinitionParser(List.of(
            (lookupClassList, field) -> null,
            (lookupClassList, field) -> IdDefinition.NOT_FOUND,
            (lookupClassList, field) -> expected
        ));

        IdDefinition actual = parser.parse(List.of(Entity.class), idField);

        assertThat(actual, sameInstance(expected));
    }

    @SneakyThrows
    @Test
    void parseShouldReturnNotFoundWhenEveryParserMisses() {
        Field idField = Entity.class.getDeclaredField("id");
        CompositeFieldDefinitionParser parser = new CompositeFieldDefinitionParser(List.of(
            (lookupClassList, field) -> null,
            (lookupClassList, field) -> IdDefinition.NOT_FOUND
        ));

        IdDefinition actual = parser.parse(List.of(Entity.class), idField);

        assertThat(actual, sameInstance(IdDefinition.NOT_FOUND));
    }

    private static class Entity {
        @SuppressWarnings("unused")
        private Long id;
    }
}
