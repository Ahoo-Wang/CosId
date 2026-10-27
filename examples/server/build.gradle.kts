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
// Example (not published): an application that reaches its store only through cosid-server (08-remote.md), with
// cosid-store-remote. compose.yaml runs the server on Redis next to it. Nothing may depend on :server, so the smoke test
// (part of `check`) starts the built server jar as its own process, on a ZooKeeper started in the test (curator-test).
plugins {
    id("cosid.java")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":spring:starter"))
    implementation(project(":store:remote"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation(libs.curator.test)
}

val serverJar = rootProject.layout.projectDirectory.file("server/build/libs/cosid-server.jar")

tasks.test {
    // The jar, not the project: a task dependency, which the dependency rules (configurations only) leave alone.
    dependsOn(":server:bootJar")
    inputs.file(serverJar).withPropertyName("serverJar")
    systemProperty("cosid.example.server.jar", serverJar.asFile.absolutePath)
}

tasks.bootJar {
    archiveFileName.set("server.jar")
}
