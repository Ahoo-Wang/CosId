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

import static java.util.Locale.ENGLISH;

import me.ahoo.cosid.accessor.CosIdAccessor;
import me.ahoo.cosid.accessor.IdDefinition;
import me.ahoo.cosid.accessor.CosIdGetter;
import me.ahoo.cosid.accessor.CosIdSetter;
import me.ahoo.cosid.accessor.DefaultCosIdAccessor;
import me.ahoo.cosid.accessor.IdTypeNotSupportException;
import me.ahoo.cosid.accessor.MultipleIdNotSupportException;
import me.ahoo.cosid.accessor.field.FieldGetter;
import me.ahoo.cosid.accessor.field.FieldSetter;
import me.ahoo.cosid.accessor.method.MethodGetter;
import me.ahoo.cosid.accessor.method.MethodSetter;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ahoo wang
 */
@Slf4j
public class DefaultAccessorParser implements CosIdAccessorParser {

    public static final String GET_PREFIX = "get";
    public static final String SET_PREFIX = "set";

    private final ConcurrentHashMap<Class<?>, CosIdAccessor> classMapAccessor = new ConcurrentHashMap<>();
    private final FieldDefinitionParser definitionParser;

    public DefaultAccessorParser(FieldDefinitionParser definitionParser) {
        this.definitionParser = definitionParser;
    }

    @Override
    public CosIdAccessor parse(Class<?> clazz) {
        return classMapAccessor.computeIfAbsent(clazz, (key) -> parseClass(clazz));
    }

    public static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        return name.substring(0, 1).toUpperCase(ENGLISH) + name.substring(1);
    }

    public static Method parseGetter(Field field) {
        String getterName = GET_PREFIX + capitalize(field.getName());
        try {
            Method method = field.getDeclaringClass().getMethod(getterName);
            if (!method.getReturnType().equals(field.getType())) {
                return null;
            }
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static Method parseSetter(Field field) {
        String setterName = SET_PREFIX + capitalize(field.getName());
        try {
            Method method = field.getDeclaringClass().getMethod(setterName, field.getType());
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    protected CosIdAccessor parseClass(Class<?> clazz) {
        CosIdAccessor firstAccessor = CosIdAccessor.NOT_FOUND;
        Class<?> currentDeclaringClass = clazz;
        while (!Object.class.equals(currentDeclaringClass)) {
            for (Field declaredField : currentDeclaringClass.getDeclaredFields()) {

                IdDefinition idDefinition = definitionParser.parse(declaredField);
                if (idDefinition == null
                    || IdDefinition.NOT_FOUND.equals(idDefinition)) {
                    continue;
                }

                if (!CosIdAccessor.NOT_FOUND.equals(firstAccessor)) {
                    throw new MultipleIdNotSupportException(clazz);
                }

                firstAccessor = definitionAsAccessor(idDefinition);

            }

            currentDeclaringClass = currentDeclaringClass.getSuperclass();
        }
        return firstAccessor;
    }

    protected CosIdAccessor definitionAsAccessor(IdDefinition idDefinition) {
        Field idField = idDefinition.getIdField();
        if (!CosIdAccessor.availableType(idField.getType())) {
            throw new IdTypeNotSupportException(idField);
        }

        if (Modifier.isFinal(idField.getModifiers())) {
            if (log.isWarnEnabled()) {
                log.warn("idField:[{}] is final.", idField);
            }
        }

        Method getter = parseGetter(idField);
        Method setter = parseSetter(idField);

        CosIdGetter cosIdGetter = getter != null ? new MethodGetter(getter) : new FieldGetter(idField);
        CosIdSetter cosIdSetter = setter != null ? new MethodSetter(setter) : new FieldSetter(idField);
        return new DefaultCosIdAccessor(idDefinition, cosIdGetter, cosIdSetter);
    }

}
