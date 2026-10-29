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

// The store compatibility kit: JUnit 5 contracts every backend runs, the in-memory reference store and fault injection
// (06-stores.md §3). Backends and core use it in test scopes only (02-modules.md §3).
plugins {
    id("cosid.library")
}

dependencies {
    api(project(":core"))
    api(libs.junit.jupiter.api)
}

// The contracts really ran against the in-memory store (roadmap R2): each backend registers the same task, with its
// own test results, so a CI job that skipped its backend fails instead of passing empty.
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
