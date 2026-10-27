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

package me.ahoo.cosid.internal;

import me.ahoo.cosid.codec.IdCodec;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

/**
 * Resolves a custom codec reference as a class name: a public {@link IdCodec} with a public no-argument constructor.
 */
public final class ClassNameCodecs {
    private ClassNameCodecs() {
    }

    /**
     * Creates the codec class named {@code reference}.
     *
     * @param reference the class name
     * @return the codec, or empty when no such class exists
     * @throws IllegalArgumentException if the class is not an {@link IdCodec} or cannot be created
     */
    public static Optional<IdCodec> resolve(String reference) {
        Class<?> type;
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            type = Class.forName(reference, true, loader == null ? ClassNameCodecs.class.getClassLoader() : loader);
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
        if (!IdCodec.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("[" + reference + "] is not an IdCodec");
        }
        try {
            return Optional.of((IdCodec) type.getConstructor().newInstance());
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException("[" + reference + "] cannot be created with a public no-argument constructor", e);
        }
    }
}
