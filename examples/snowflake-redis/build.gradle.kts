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
// Example (not published): Snowflake ids whose machine slot is leased from Redis. Run it with
// `REDIS_URI=redis://localhost:6379 ./gradlew :examples:snowflake-redis:bootRun`; its smoke test (part of `check`)
// uses COSID_TEST_REDIS_URI or a Redis in Testcontainers.
plugins {
    id("cosid.java")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":spring:starter"))
    implementation(project(":store:redis"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation(libs.testcontainers)
}

tasks.test {
    inputs.property("redisUri", providers.environmentVariable("COSID_TEST_REDIS_URI").orElse(""))
}
