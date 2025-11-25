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

package me.ahoo.cosid.spring.boot.starter;

import org.springframework.util.StringUtils;

/**
 * Utility class for namespace operations in CosId configuration.
 *
 * <p>This class provides helper methods for working with namespaces,
 * which are used to isolate ID generation contexts in distributed systems.</p>
 *
 * @author ahoo wang
 */
public final class Namespaces {
    private Namespaces() {
    }

    /**
     * Returns the first non-blank namespace from the provided arguments.
     *
     * <p>This method iterates through the provided namespace strings and returns
     * the first one that is not null, empty, or contains only whitespace characters.
     * This is commonly used to determine the effective namespace by checking
     * specific configuration first, then falling back to global defaults.</p>
     *
     * @param namespaces the namespace strings to check, in order of preference
     * @return the first non-blank namespace
     * @throws IllegalArgumentException if all provided namespaces are blank
     */
    public static String firstNotBlank(String... namespaces) {
        for (String namespace : namespaces) {
            if (StringUtils.hasText(namespace)) {
                return namespace;
            }
        }
        throw new IllegalArgumentException("namespaces can not be all blank!");
    }
}
