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

// The Redis store (06-stores.md §2.2): Lua scripts behind the minimal RedisScripts interface, run by Lettuce. No Spring
// dependency (D6); the Spring Data adapter lives in spring/autoconfigure.
plugins {
    id("cosid.library")
}

dependencies {
    api(project(":core"))
    api(libs.lettuce.core)
    testImplementation(project(":store:tck"))
    testImplementation(libs.testcontainers)
}

tasks.test {
    // Standalone tests use COSID_TEST_REDIS_URI when set (AGENTS.md), Testcontainers otherwise; the replica and
    // cluster tests always use Testcontainers.
    systemProperty("cosid.test.redis.image", providers.gradleProperty("cosid.test.redis.image").getOrElse("redis:7.4-alpine"))
}

// The contracts really ran against Redis (roadmap R2): a run that skipped them fails, as in store/tck.
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
