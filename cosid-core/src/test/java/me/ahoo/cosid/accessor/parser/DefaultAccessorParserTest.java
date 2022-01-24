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

import me.ahoo.cosid.accessor.CosIdAccessor;
import me.ahoo.cosid.accessor.DefaultCosIdAccessor;
import me.ahoo.cosid.accessor.IdTypeNotSupportException;
import me.ahoo.cosid.accessor.MultipleIdNotSupportException;
import me.ahoo.cosid.accessor.field.FieldGetter;
import me.ahoo.cosid.accessor.field.FieldSetter;
import me.ahoo.cosid.accessor.method.MethodGetter;
import me.ahoo.cosid.accessor.method.MethodSetter;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.annotation.entity.ChildEntity;
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.annotation.entity.LongIdEntity;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
class DefaultAccessorParserTest {

    public static final DefaultAccessorParser ACCESSOR_PARSER = new DefaultAccessorParser(AnnotationDefinitionParser.INSTANCE);

    @SneakyThrows
    @Test
    void parse() {
        CosIdAccessor cosIdAccessor = ACCESSOR_PARSER.parse(LongIdEntity.class);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(IdGeneratorProvider.SHARE, cosIdAccessor.getGeneratorName());
        Assertions.assertEquals(LongIdEntity.class, cosIdAccessor.getIdDeclaringClass());
        Assertions.assertEquals(LongIdEntity.class.getDeclaredField("id"), cosIdAccessor.getIdField());
    }

    @SneakyThrows
    @Test
    void parseInt() {
        CosIdAccessor cosIdAccessor = ACCESSOR_PARSER.parse(IntIdType.class);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(IdGeneratorProvider.SHARE, cosIdAccessor.getGeneratorName());
        Assertions.assertEquals(IntIdType.class, cosIdAccessor.getIdDeclaringClass());
        Assertions.assertEquals(IntIdType.class.getDeclaredField("id"), cosIdAccessor.getIdField());
    }

    @SneakyThrows
    @Test
    void parseNamed() {
        CosIdAccessor cosIdAccessor = ACCESSOR_PARSER.parse(NamedIdType.class);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals("namedId", cosIdAccessor.getGeneratorName());
        Assertions.assertEquals(NamedIdType.class, cosIdAccessor.getIdDeclaringClass());
        Assertions.assertEquals(NamedIdType.class.getDeclaredField("id"), cosIdAccessor.getIdField());
    }

    @SneakyThrows
    @Test
    void parseMultipleField() {
        CosIdAccessor cosIdAccessor = ACCESSOR_PARSER.parse(MultipleField.class);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(IdGeneratorProvider.SHARE, cosIdAccessor.getGeneratorName());
        Assertions.assertEquals(MultipleField.class, cosIdAccessor.getIdDeclaringClass());
        Assertions.assertEquals(MultipleField.class.getDeclaredField("id"), cosIdAccessor.getIdField());
    }

    @SneakyThrows
    @Test
    void parseInherited() {
        CosIdAccessor cosIdAccessor = ACCESSOR_PARSER.parse(ChildEntity.class);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(IdGeneratorProvider.SHARE, cosIdAccessor.getGeneratorName());
        Assertions.assertEquals(LongIdEntity.class, cosIdAccessor.getIdDeclaringClass());
        Assertions.assertEquals(LongIdEntity.class.getDeclaredField("id"), cosIdAccessor.getIdField());
    }

    @SneakyThrows
    @Test
    void getCosIdAccessor() {

        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) ACCESSOR_PARSER.parse(LongIdEntity.class);

        Assertions.assertEquals(LongIdEntity.class.getDeclaredField("id"), cosIdAccessor.getIdField());

        Assertions.assertTrue(cosIdAccessor.getGetter() instanceof MethodGetter);
        Assertions.assertTrue(cosIdAccessor.getSetter() instanceof MethodSetter);
    }


    @Test
    void wrongIdType() {
        Assertions.assertThrows(IdTypeNotSupportException.class, () -> {
            ACCESSOR_PARSER.parse(WrongIdType.class);
        });
    }

    @SneakyThrows
    @Test
    void noGetterSetter() {
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) ACCESSOR_PARSER.parse(NoGetterSetter.class);

        Assertions.assertEquals(NoGetterSetter.class.getDeclaredField("id"), cosIdAccessor.getIdField());

        Assertions.assertTrue(cosIdAccessor.getGetter() instanceof FieldGetter);
        Assertions.assertTrue(cosIdAccessor.getSetter() instanceof FieldSetter);
    }

    @SneakyThrows
    @Test
    void finalId() {
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) ACCESSOR_PARSER.parse(FinalId.class);

        Assertions.assertEquals(FinalId.class.getDeclaredField("id"), cosIdAccessor.getIdField());

        FinalId finalId = new FinalId();
        Assertions.assertEquals(0L, cosIdAccessor.getId(finalId));
        cosIdAccessor.setId(finalId, 1);
        Assertions.assertEquals(1L, cosIdAccessor.getId(finalId));
    }

    @SneakyThrows
    @Test
    void child() {
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) ACCESSOR_PARSER.parse(ChildEntity.class);
        Assertions.assertEquals(LongIdEntity.class.getDeclaredField("id"), cosIdAccessor.getIdField());
    }

    @Test
    void multipleId() {
        Assertions.assertThrows(MultipleIdNotSupportException.class, () -> {
            ACCESSOR_PARSER.parse(MultipleId.class);
        });
    }

    @SneakyThrows
    @Test
    void classId() {
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) ACCESSOR_PARSER.parse(ClassIdType.class);
        Assertions.assertEquals(ClassIdType.class.getDeclaredField("id"), cosIdAccessor.getIdField());
    }

    @SneakyThrows
    @Test
    void classIdInherited() {
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) ACCESSOR_PARSER.parse(ClassIdInheritedType.class);
        Assertions.assertEquals(AbstractId.class.getDeclaredField("id"), cosIdAccessor.getIdField());
    }

    @SneakyThrows
    @Test
    void classCosIdInherited() {
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) ACCESSOR_PARSER.parse(ClassCosIdInheritedType.class);
        Assertions.assertEquals(AbstractCosId.class.getDeclaredField("id"), cosIdAccessor.getIdField());
    }


    @SneakyThrows
    @Test
    void abstractGenericCosId() {
        Assertions.assertThrows(IdTypeNotSupportException.class, () -> {
            ACCESSOR_PARSER.parse(AbstractGenericCosId.class);
        });
    }

    @SneakyThrows
    @Test
    void classIdGenericInherited() {
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) ACCESSOR_PARSER.parse(ClassIdGenericInheritedType.class);
        Assertions.assertEquals(AbstractGenericCosId.class.getDeclaredField("id"), cosIdAccessor.getIdField());
    }

    @SneakyThrows
    @Test
    void classIdGenericFourInherited() {
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) ACCESSOR_PARSER.parse(ClassIdGenericFourInheritedType.class);
        Assertions.assertEquals(AbstractGenericCosId.class.getDeclaredField("id"), cosIdAccessor.getIdField());
    }


    @Test
    void capitalize() {
    }

    @Test
    void parseGetter() {
    }

    @Test
    void parseSetter() {
    }

    @Test
    void parseClass() {
    }

    @Test
    void definitionAsAccessor() {
    }

    public static class WrongIdType {
        @CosId
        private double id;

        public double getId() {
            return id;
        }

        public void setId(double id) {
            this.id = id;
        }
    }

    public static class IntIdType {
        @CosId
        private int id;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    public static class NamedIdType {
        @CosId("namedId")
        private long id;

        public long getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    public static class NoGetterSetter {
        @CosId
        private long id;
    }

    public static class FinalId {
        @CosId
        private final long id = 0;
    }

    public static class MultipleId {
        @CosId
        private long id;
        @CosId
        private long id2;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public long getId2() {
            return id2;
        }

        public void setId2(long id2) {
            this.id2 = id2;
        }
    }


    public static class MultipleField {
        @CosId
        private long id;

        private String name;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @CosId(field = "id")
    public static class ClassIdType {

        private long id;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }
    }

    public abstract static class AbstractId {
        private long id;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }
    }

    @CosId(field = "id")
    public static class ClassIdInheritedType extends AbstractId {

    }

    @CosId(field = "id")
    public abstract static class AbstractCosId {
        private long id;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }
    }

    public static class ClassCosIdInheritedType extends AbstractCosId {

    }

    @CosId(field = "id")
    public abstract static class AbstractGenericCosId<T, N> {
        private T id;
        private N name;

        public T getId() {
            return id;
        }

        public void setId(T id) {
            this.id = id;
        }

        public N getName() {
            return name;
        }

        public void setName(N name) {
            this.name = name;
        }
    }


    public static class ClassIdGenericInheritedType extends AbstractGenericCosId<Long, String> {

    }

    public static class ClassIdGenericTwoInheritedType<T, O> extends AbstractGenericCosId<O, T> {

    }


    public static class ClassIdGenericThreeInheritedType<H> extends ClassIdGenericTwoInheritedType<String, H> {

    }

    public static class ClassIdGenericFourInheritedType extends ClassIdGenericThreeInheritedType<Long> {

    }

}
