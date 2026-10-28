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

package me.ahoo.cosid.store;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The keys every backend stores alike (R1-spi-02, 06-stores.md §1): namespaces, the names of generators and machines,
 * and the range and owner keys made from them. A key that one backend cannot store fails at start-up on every backend,
 * so a configuration moves between backends unchanged.
 *
 * <ul>
 *   <li>A namespace: 1 to {@value #MAX_NAMESPACE_LENGTH} of {@code A-Z a-z 0-9 . _ : -}, starting with a letter or digit
 *       (the JDBC {@code namespace} column; a ZooKeeper node name; a Redis hash tag).</li>
 *   <li>A name: not empty, without {@code /}, not {@code .} nor {@code ..}, without whitespace (SQL Server ignores
 *       trailing spaces when it compares; cosid-server refuses blanks in a range key), and without a character
 *       ZooKeeper refuses in a path: controls, surrogates, {@code U+E000..U+F8FF} and {@code U+FFF0..U+FFFF}.</li>
 *   <li>A range key ({@code namespace/name}, {@code namespace/name/period}) and an owner key: at most
 *       {@value #MAX_KEY_LENGTH} UTF-16 units (the JDBC {@code varchar(255)} / {@code nvarchar(255)} columns), without
 *       leading or trailing whitespace nor a character ZooKeeper refuses.</li>
 * </ul>
 */
public final class StoreKeys {
    /** The longest namespace. */
    public static final int MAX_NAMESPACE_LENGTH = 100;
    /** The longest range key or owner key. */
    public static final int MAX_KEY_LENGTH = 255;
    private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0," + (MAX_NAMESPACE_LENGTH - 1) + "}");

    private StoreKeys() {
    }

    /**
     * Whether every backend stores this namespace.
     *
     * @param namespace the namespace
     * @return true if it is portable
     */
    public static boolean isNamespace(String namespace) {
        return namespace != null && NAMESPACE.matcher(namespace).matches();
    }

    /**
     * Why a namespace is not portable.
     *
     * @param namespace the namespace
     * @return the problem; empty when it is portable
     */
    public static Optional<String> namespaceProblem(String namespace) {
        return isNamespace(namespace) ? Optional.empty()
            : Optional.of("namespace [" + namespace + "] must be 1 to " + MAX_NAMESPACE_LENGTH + " of A-Z a-z 0-9 . _ : -, starting with a letter or digit");
    }

    /**
     * Why a generator or machine name is not portable.
     *
     * @param name the name
     * @return the problem; empty when it is portable
     */
    public static Optional<String> nameProblem(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.of("the name must not be empty");
        }
        if (name.indexOf('/') >= 0) {
            return Optional.of("the name must not contain '/'");
        }
        if (".".equals(name) || "..".equals(name)) {
            return Optional.of("the name must not be '" + name + "'");
        }
        if (name.chars().anyMatch(Character::isWhitespace)) {
            return Optional.of("the name must not contain whitespace");
        }
        return textProblem("name", name);
    }

    /**
     * Why a range key or owner key is not portable.
     *
     * @param what the kind of key, for the problem
     * @param key  the key
     * @return the problem; empty when it is portable
     */
    public static Optional<String> keyProblem(String what, String key) {
        if (key.isEmpty()) {
            return Optional.of("the " + what + " must not be empty");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            return Optional.of("the " + what + " [" + key + "] is longer than " + MAX_KEY_LENGTH + " characters: " + key.length());
        }
        return textProblem(what, key);
    }

    private static Optional<String> textProblem(String what, String text) {
        if (Character.isWhitespace(text.charAt(0)) || Character.isWhitespace(text.charAt(text.length() - 1))) {
            return Optional.of("the " + what + " [" + text + "] must not start or end with whitespace");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c >= 0x7F && c <= 0x9F || c >= 0xD800 && c <= 0xF8FF || c >= 0xFFF0) {
                return Optional.of("the " + what + " [" + text + "] holds the character U+" + String.format("%04X", (int) c)
                    + ", which ZooKeeper cannot store");
            }
        }
        return Optional.empty();
    }
}
