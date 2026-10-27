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

// The MongoDB store (06-stores.md §2.3): ranges and slot leases over the sync driver, MongoDB 6.0 or later.
plugins {
    id("cosid.library")
}

dependencies {
    api(project(":core"))
    // MongoStore takes a MongoDatabase and the provider an injected MongoClient: the driver is part of the API.
    api(libs.mongodb.driver.sync)
    implementation(libs.slf4j.api)
    testImplementation(project(":store:tck"))
    testImplementation(libs.testcontainers.mongodb)
    // Captures the startup warning of a write concern weaker than majority.
    testImplementation(libs.logback.classic)
}

// The contracts ran against MongoDB (roadmap R2), as in store/tck: a run that skipped them fails.
val checkContractsRan by tasks.registering(JavaExec::class) {
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
