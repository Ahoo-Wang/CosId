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

package me.ahoo.cosid.annotation.accessor;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.annotation.CosIdDefinition;
import me.ahoo.cosid.annotation.accessor.field.FieldGetter;
import me.ahoo.cosid.annotation.accessor.field.FieldSetter;
import me.ahoo.cosid.annotation.accessor.method.MethodGetter;
import me.ahoo.cosid.annotation.accessor.method.MethodSetter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Locale.ENGLISH;

/**
 * @author ahoo wang
 */
@Slf4j
public abstract class CosIdAccessorSupport {

    public static final String GET_PREFIX = "get";
    public static final String SET_PREFIX = "set";

    private static final ConcurrentHashMap<Class<?>, CosIdAccessor> classMapAccessor = new ConcurrentHashMap<>();

    public static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        return name.substring(0, 1).toUpperCase(ENGLISH) + name.substring(1);
    }

    public static Method resolveGetter(Class<?> declaringClass, Field field) {
        String getterName = GET_PREFIX + capitalize(field.getName());
        try {
            Method method = declaringClass.getMethod(getterName);
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

    public static Method resolveSetter(Class<?> declaringClass, Field field) {
        String setterName = SET_PREFIX + capitalize(field.getName());
        try {
            Method method = declaringClass.getMethod(setterName, field.getType());
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static CosIdAccessor getCosIdAccessor(Class<?> declaringClass) {
        return classMapAccessor.computeIfAbsent(declaringClass, (key) -> getCosIdAccessor0(declaringClass));
    }

    private static CosIdAccessor getCosIdAccessor0(Class<?> declaringClass) {
        CosIdAccessor firstAccessor = CosIdAccessor.NOT_FOUND;
        Class<?> currentDeclaringClass = declaringClass;
        while (!Object.class.equals(currentDeclaringClass)) {

            for (Field declaredField : currentDeclaringClass.getDeclaredFields()) {
                if (!declaredField.isAnnotationPresent(CosId.class)) {
                    continue;
                }

                if (!CosIdAccessor.NOT_FOUND.equals(firstAccessor)) {
                    throw new MultipleIdNotSupportException(declaringClass);
                }

                if (!CosIdAccessor.availableType(declaredField.getType())) {
                    throw new IdTypeNotSupportException(declaredField);
                }

                if (Modifier.isFinal(declaredField.getModifiers())) {
                    if (log.isWarnEnabled()) {
                        log.warn("idField:[{}] is final.", declaredField);
                    }
                }

                CosId cosId = declaredField.getAnnotation(CosId.class);
                CosIdDefinition cosIdDefinition = CosIdDefinition.of(cosId);
                Method getter = resolveGetter(currentDeclaringClass, declaredField);
                Method setter = resolveSetter(currentDeclaringClass, declaredField);

                CosIdGetter cosIdGetter = getter != null ? new MethodGetter(cosIdDefinition, declaredField, getter) : new FieldGetter(cosIdDefinition, declaredField);
                CosIdSetter cosIdSetter = setter != null ? new MethodSetter(cosIdDefinition, declaredField, setter) : new FieldSetter(cosIdDefinition, declaredField);
                firstAccessor = new DefaultCosIdAccessor(cosIdGetter, cosIdSetter);
            }

            currentDeclaringClass = currentDeclaringClass.getSuperclass();
        }
        return firstAccessor;
    }
}
