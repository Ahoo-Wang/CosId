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

package me.ahoo.cosid.annotation;

import me.ahoo.cosid.accessor.IdDefinition;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class CosIdAnnotationAccessorParserTest {

    @SneakyThrows
    @Test
    void parseShouldUseFieldLevelAnnotation() {
        Field idField = FieldAnnotatedEntity.class.getDeclaredField("id");

        IdDefinition idDefinition = AnnotationDefinitionParser.INSTANCE.parse(List.of(FieldAnnotatedEntity.class), idField);

        assertThat(idDefinition, not(sameInstance(IdDefinition.NOT_FOUND)));
        assertThat(idDefinition.getGeneratorName(), equalTo("fieldGenerator"));
        assertThat(idDefinition.getIdField(), equalTo(idField));
        assertThat(idDefinition.getIdType(), equalTo(Long.class));
    }

    @SneakyThrows
    @Test
    void parseShouldUseTypeLevelAnnotationWhenFieldNameMatches() {
        Field orderIdField = TypeAnnotatedEntity.class.getDeclaredField("orderId");

        IdDefinition idDefinition = AnnotationDefinitionParser.INSTANCE.parse(List.of(TypeAnnotatedEntity.class), orderIdField);

        assertThat(idDefinition, not(sameInstance(IdDefinition.NOT_FOUND)));
        assertThat(idDefinition.getGeneratorName(), equalTo("typeGenerator"));
        assertThat(idDefinition.getIdField(), equalTo(orderIdField));
        assertThat(idDefinition.getIdType(), equalTo(String.class));
    }

    @SneakyThrows
    @Test
    void parseShouldReturnNotFoundWhenTypeLevelFieldNameDoesNotMatch() {
        Field otherField = TypeAnnotatedEntity.class.getDeclaredField("other");

        IdDefinition idDefinition = AnnotationDefinitionParser.INSTANCE.parse(List.of(TypeAnnotatedEntity.class), otherField);

        assertThat(idDefinition, sameInstance(IdDefinition.NOT_FOUND));
    }

    @SneakyThrows
    @Test
    void parseShouldPreferFieldLevelAnnotationOverTypeLevelAnnotation() {
        Field idField = TypeAndFieldAnnotatedEntity.class.getDeclaredField("id");

        IdDefinition idDefinition = AnnotationDefinitionParser.INSTANCE.parse(List.of(TypeAndFieldAnnotatedEntity.class), idField);

        assertThat(idDefinition.getGeneratorName(), equalTo("fieldGenerator"));
    }

    @SneakyThrows
    @Test
    void parseShouldUseSharedGeneratorForDefaultAnnotationValue() {
        Field idField = DefaultAnnotatedEntity.class.getDeclaredField("id");

        IdDefinition idDefinition = AnnotationDefinitionParser.INSTANCE.parse(List.of(DefaultAnnotatedEntity.class), idField);

        assertThat(idDefinition.getGeneratorName(), equalTo(IdGeneratorProvider.SHARE));
    }

    @SneakyThrows
    @Test
    void parseShouldReturnNotFoundWhenNoAnnotationExists() {
        Field idField = NotAnnotatedEntity.class.getDeclaredField("id");

        IdDefinition idDefinition = AnnotationDefinitionParser.INSTANCE.parse(List.of(NotAnnotatedEntity.class), idField);

        assertThat(idDefinition, sameInstance(IdDefinition.NOT_FOUND));
    }

    private static class FieldAnnotatedEntity {
        @CosId("fieldGenerator")
        private Long id;
    }

    @CosId(value = "typeGenerator", field = "orderId")
    private static class TypeAnnotatedEntity {
        private String orderId;

        private String other;
    }

    @CosId(value = "typeGenerator", field = "id")
    private static class TypeAndFieldAnnotatedEntity {
        @CosId("fieldGenerator")
        private Long id;
    }

    private static class DefaultAnnotatedEntity {
        @CosId
        private Long id;
    }

    private static class NotAnnotatedEntity {
        private Long id;
    }
}
