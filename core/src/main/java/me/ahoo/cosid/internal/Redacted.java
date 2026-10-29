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

import me.ahoo.cosid.store.StoreOptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Store options as a {@code toString} shows them, without their secrets (R2-core-02): the value of a key whose
 * canonical form names a password, token, secret or credential is {@code ***}, and a URL loses the password of its
 * user info and its {@code password=} / {@code pwd=} parameters.
 */
public final class Redacted {
    private static final List<String> SECRETS = List.of("password", "pwd", "token", "secret", "credential");
    private static final Pattern USER_INFO = Pattern.compile("(//[^/@:?#;]*):[^/@?#;]*@");
    private static final Pattern PASSWORD_PARAMETER = Pattern.compile("(?i)([?&;](?:password|pwd)=)[^&;]*");

    private Redacted() {
    }

    /**
     * The options without their secrets, in their order.
     *
     * @param options the options
     * @return a new map
     */
    public static Map<String, Object> options(Map<String, ?> options) {
        Map<String, Object> shown = new LinkedHashMap<>();
        options.forEach((key, value) -> shown.put(key, secret(key) ? "***" : value instanceof String text ? url(text) : value));
        return shown;
    }

    /**
     * Whether an option holds a secret.
     *
     * @param key the option key, in any spelling
     * @return true for a password, token, secret or credential
     */
    public static boolean secret(String key) {
        String canonical = StoreOptions.canonical(key);
        return SECRETS.stream().anyMatch(canonical::contains);
    }

    /**
     * A text without the password of a URL in it.
     *
     * @param text the text
     * @return {@code //user:***@host}, {@code ?password=***}
     */
    public static String url(String text) {
        return PASSWORD_PARAMETER.matcher(USER_INFO.matcher(text).replaceAll("$1:***@")).replaceAll("$1***");
    }
}
