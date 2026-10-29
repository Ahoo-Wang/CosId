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
// Example (not published): segment and periodic-segment generators on the JDBC store, H2 in memory, schema created on
// start. Run it with `./gradlew :examples:segment-jdbc:bootRun`; its smoke test is part of `check`.
plugins {
    id("cosid.java")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":spring:starter"))
    implementation(project(":store:jdbc"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly(libs.h2)
}
