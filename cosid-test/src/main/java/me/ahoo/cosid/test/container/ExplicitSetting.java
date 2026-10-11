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

package me.ahoo.cosid.test.container;

import java.util.Optional;

/**
 * Resolves an explicitly configured test setting: the system property wins over the environment variable.
 */
final class ExplicitSetting {

    private ExplicitSetting() {
    }

    static Optional<String> resolve(String propertyName, String environmentName) {
        return firstNonBlank(System.getProperty(propertyName), System.getenv(environmentName));
    }

    static Optional<String> firstNonBlank(String propertyValue, String environmentValue) {
        Optional<String> propertySetting = trimToOptional(propertyValue);
        if (propertySetting.isPresent()) {
            return propertySetting;
        }
        return trimToOptional(environmentValue);
    }

    private static Optional<String> trimToOptional(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(trimmedValue);
    }
}
