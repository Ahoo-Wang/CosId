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

package me.ahoo.cosid.segment.grouped;

import com.google.errorprone.annotations.ThreadSafe;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Thread-local accessor for managing grouped key context.
 *
 * <p>Provides thread-local storage for the current group key,
 * allowing different threads to work with different group contexts.
 *
 * @author ahoo wang
 */
@ThreadSafe
public final class GroupedAccessor {
    private static final ThreadLocal<GroupedKey> CURRENT = new ThreadLocal<>();

    /**
     * Sets the grouped key for the current thread.
     *
     * @param groupedKey the key to set
     */
    public static void set(GroupedKey groupedKey) {
        CURRENT.set(groupedKey);
    }

    /**
     * Sets the grouped key if it's not NEVER.
     *
     * @param groupedKey the key to set
     */
    public static void setIfNotNever(GroupedKey groupedKey) {
        if (GroupedKey.NEVER.equals(groupedKey)) {
            return;
        }
        set(groupedKey);
    }

    /**
     * Gets the grouped key for the current thread.
     *
     * @return the key or null if not set
     */
    public static GroupedKey get() {
        return CURRENT.get();
    }

    /**
     * Gets the grouped key, throwing if not set.
     *
     * @return the key
     * @throws NullPointerException if not set
     */
    @NonNull
    public static GroupedKey requiredGet() {
        return Objects.requireNonNull(get(), "The current thread has not set the GroupedKey.");
    }

    /**
     * Clears the grouped key for the current thread.
     */
    public static void clear() {
        CURRENT.remove();
    }
}
