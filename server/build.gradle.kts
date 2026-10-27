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

// cosid-server (08-remote.md): a Spring Boot application, not a library. It exposes the two store primitives of its
// backing store (cosid.stores) at the root (no version prefix) and, optionally, the generators of its CosIdRuntime. Nothing may depend on it
// (DependencyRules); its tests run the store TCK through store/remote against a server started in process.
plugins {
    id("cosid.java")
    alias(libs.plugins.spring.boot)
}

// The Spring Boot BOM on this project's classpaths; -PspringBootVersion swaps it, as cosid.spring does for spring/*.
val springBootVersion = findProperty("springBootVersion")?.toString()
val springBootBom = if (springBootVersion == null) dependencies.platform(libs.spring.boot.dependencies)
else dependencies.enforcedPlatform("org.springframework.boot:spring-boot-dependencies:$springBootVersion")

dependencies {
    implementation(springBootBom)
    annotationProcessor(springBootBom)
    implementation(project(":core"))
    implementation(project(":spring:autoconfigure"))
    // RemoteApi: the paths and error codes the client and the server share.
    implementation(project(":store:remote"))
    implementation(project(":store:jdbc"))
    implementation(project(":store:redis"))
    implementation(project(":store:mongo"))
    implementation(project(":store:zookeeper"))
    implementation(libs.hikari)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation(libs.springdoc.openapi.webmvc.ui)
    runtimeOnly(libs.mysql.connector)
    runtimeOnly(libs.mariadb.client)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.mssql.jdbc)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(project(":store:tck"))
    testImplementation(libs.h2)
}

tasks.test {
    // The Redis contracts run only with it: an input, so a cached run without Redis is not reused with it (and back).
    inputs.property("redisUri", providers.environmentVariable("COSID_TEST_REDIS_URI").orElse(""))
}

tasks.bootJar {
    archiveFileName.set("cosid-server.jar")
}

// The image of 08 §4: Dockerfile on the boot jar. CI builds it (job "Server"); bootBuildImage (buildpacks) works too.
tasks.register<Exec>("dockerImage") {
    description = "Builds the cosid-server Docker image from the boot jar."
    group = "build"
    dependsOn(tasks.bootJar)
    workingDir = projectDir
    commandLine("docker", "build", "-t", "ahoowang/cosid-server:${project.version}", ".")
}

// The store TCK ran through store/remote against a real server (roadmap R4-A), as in store/tck: skipped cases fail.
val checkContractsRan = tasks.register<JavaExec>("checkContractsRan") {
    description = "Checks from the JUnit reports that every store contract case ran."
    group = "verification"
    val reports = layout.buildDirectory.dir("test-results/test")
    dependsOn(tasks.test)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.ahoo.cosid.store.tck.ContractsRan")
    argumentProviders.add(CommandLineArgumentProvider { listOf(reports.get().asFile.absolutePath) })
}
tasks.check {
    dependsOn(checkContractsRan)
}
