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

package me.ahoo.cosid.test;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Counts the JVM shutdown hooks, to check that a component does not leave one behind.
 *
 * <p>Reads {@code java.lang.ApplicationShutdownHooks}, which test JVMs open with {@code --add-opens java.base/java.lang}.
 */
public final class ShutdownHooks {
    private ShutdownHooks() {
    }

    /**
     * The number of registered shutdown hooks.
     *
     * @return the number of registered shutdown hooks
     */
    public static int count() {
        try {
            Class<?> hooksClass = Class.forName("java.lang.ApplicationShutdownHooks");
            Field hooksField = hooksClass.getDeclaredField("hooks");
            hooksField.setAccessible(true);
            synchronized (hooksClass) {
                return ((Map<?, ?>) hooksField.get(null)).size();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Can not read the shutdown hooks.", e);
        }
    }
}
