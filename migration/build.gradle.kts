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

// The offline 4.x → 5.0 migration tool (docs/architecture/09-migration.md): an executable fat jar,
// build/libs/cosid-migration.jar, not published. It reads the 4.x formats itself (read-only, except for sealing)
// and writes the 5.0 schemas of store/* through its own narrow admin path.
plugins {
    id("cosid.java")
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":store:jdbc"))
    implementation(project(":store:redis"))
    implementation(project(":store:mongo"))
    implementation(project(":store:zookeeper"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.mysql.connector)
    runtimeOnly(libs.mariadb.client)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.mssql.jdbc)
    runtimeOnly(libs.h2)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.curator.test)
    testImplementation(libs.h2)
}

// The 4.x writers of the tests (09 §5): compiled against the released 4.1.0 jars only, and loaded at test time in a
// class loader of their own, since 4.x and 5.0 share the package me.ahoo.cosid. The sources live under src/test, so
// Checkstyle checks their license header only.
val legacy4x: SourceSet by sourceSets.creating {
    java.setSrcDirs(listOf("src/test/legacy4x"))
    resources.setSrcDirs(emptyList<String>())
}
dependencies {
    "legacy4xImplementation"(libs.cosid4.core)
    "legacy4xImplementation"(libs.cosid4.jdbc)
    "legacy4xImplementation"(libs.cosid4.spring.redis)
    "legacy4xImplementation"(libs.cosid4.lettuce)
    "legacy4xImplementation"(libs.cosid4.mongo)
    "legacy4xImplementation"(libs.mongodb.driver.sync)
    "legacy4xImplementation"(libs.cosid4.zookeeper)
    "legacy4xImplementation"(libs.curator.framework)
    "legacy4xRuntimeOnly"(libs.h2)
}
tasks.named("spotbugsLegacy4x") {
    enabled = false
}

tasks.test {
    val legacyClasspath = legacy4x.runtimeClasspath
    inputs.files(legacyClasspath).withPropertyName("legacy4xClasspath").withNormalizer(ClasspathNormalizer::class.java)
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dcosid.migration.legacy4x=" + legacyClasspath.asPath) })
    // -Pcosid.migration.updateGoldens=true rewrites the config goldens (ConfigGoldenTest).
    systemProperty("cosid.migration.updateGoldens", providers.gradleProperty("cosid.migration.updateGoldens").getOrElse("false"))
}

tasks.shadowJar {
    archiveFileName.set("cosid-migration.jar")
    // The JDBC drivers and the store providers each bring META-INF/services files of the same name: merge them
    // (Shadow hands duplicates to its transformers only when they are included).
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "me.ahoo.cosid.migration.Main"
    }
}
tasks.assemble {
    dependsOn(tasks.shadowJar)
}
