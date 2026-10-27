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

package me.ahoo.cosid.spring.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;

/**
 * The compatibility matrix really runs on the Spring Boot and Java it names (-PspringBootVersion, -PtestJavaVersion).
 */
class CompatibilityTest {
    @Test
    void runsOnTheRequestedVersions() {
        String boot = System.getenv("COSID_TEST_SPRING_BOOT_VERSION");
        if (boot != null) {
            assertEquals(boot, SpringBootVersion.getVersion());
        }
        String java = System.getenv("COSID_TEST_JAVA_VERSION");
        if (java != null) {
            assertEquals(Integer.parseInt(java), Runtime.version().feature());
        }
    }
}
