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

package me.ahoo.cosid.spring.boot.starter.store;

import org.jspecify.annotations.Nullable;

/**
 * Store types ({@code cosid.machine.distributor.type}, {@code cosid.segment.distributor.type}) compare like Spring's
 * relaxed binding of enums: only letters and digits count, without case, so {@code stateful_set}, {@code stateful-set},
 * {@code STATEFUL_SET}, {@code statefulSet} and {@code statefulset} are one type.
 *
 * @since 4.0.0
 */
public final class StoreTypes {
    private StoreTypes() {
    }
    
    /**
     * The canonical form of a store type: its letters and digits, in lower case.
     *
     * @param defaultType the type when {@code type} is blank
     */
    public static String normalize(@Nullable String type, String defaultType) {
        String value = type == null || type.isBlank() ? defaultType : type;
        StringBuilder canonical = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                canonical.append(Character.toLowerCase(c));
            }
        }
        return canonical.toString();
    }
    
    /**
     * Whether two store types are the same.
     */
    public static boolean same(@Nullable String left, @Nullable String right) {
        return left != null && right != null && normalize(left, left).equals(normalize(right, right));
    }
}
