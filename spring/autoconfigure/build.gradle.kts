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

// The Spring Boot auto-configuration (07-runtime-and-spring.md §4): a thin shell around CosIdRuntime. Stores and the
// optional Spring modules are compileOnly behind @ConditionalOnClass (02-modules.md §3); the application brings them.
plugins {
    id("cosid.spring")
}

dependencies {
    api(project(":core"))
    api(libs.spring.boot.autoconfigure)
    compileOnly(project(":store:jdbc"))
    compileOnly(project(":store:redis"))
    compileOnly(project(":store:mongo"))
    compileOnly(project(":store:zookeeper"))
    compileOnly("org.springframework.boot:spring-boot-actuator")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-health")
    compileOnly("io.micrometer:micrometer-core")
    // Health carries Jackson annotations; without them javac warns.
    compileOnly("com.fasterxml.jackson.core:jackson-annotations")
    compileOnly("org.springframework.data:spring-data-redis")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")

    testImplementation(project(":store:tck"))
    testImplementation(project(":store:jdbc"))
    testImplementation(project(":store:redis"))
    testImplementation(project(":store:mongo"))
    testImplementation(project(":store:zookeeper"))
    testImplementation("org.springframework.boot:spring-boot-test")
    // ApplicationContextRunner's assertable context
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework.boot:spring-boot-actuator")
    testImplementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-health")
    testImplementation("org.springframework.boot:spring-boot-micrometer-metrics")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.springframework.data:spring-data-redis")
    testImplementation(libs.h2)
    testImplementation(libs.testcontainers)
}

tasks.test {
    // The Redis smoke tests use COSID_TEST_REDIS_URI when set, Testcontainers on this image otherwise.
    systemProperty("cosid.test.redis.image", providers.gradleProperty("cosid.test.redis.image").getOrElse("redis:8.10-alpine"))
}
