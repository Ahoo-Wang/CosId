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

// The ZooKeeper store (06-stores.md §2.4): RangeStore and SlotLeaseStore on Curator. Depends on core and Curator only.
plugins {
    id("cosid.library")
}

dependencies {
    api(project(":core"))
    // ZooKeeper brings Netty 4.1 (transport-native-epoll among it) for a socket the client never uses by default
    // (ClientCnxnSocketNIO); next to Lettuce 7's Netty 4.2 it breaks Redis on Linux (EpollIoHandler). Excluded, and
    // published as an exclusion so consumers get the same classpath.
    api(libs.curator.framework) {
        exclude(group = "io.netty")
    }
    // The patched ZooKeeper and Guava over Curator's (security advisories): a direct dependency wins in Maven
    // (nearest) and in Gradle (highest).
    api(libs.zookeeper) {
        exclude(group = "io.netty")
    }
    runtimeOnly(libs.guava)
    implementation(libs.slf4j.api)
    testImplementation(project(":store:tck"))
    testImplementation(libs.curator.test)
}

tasks.test {
    jvmArgs("-Dlogback.configurationFile=${layout.projectDirectory.file("src/test/resources/logback-zookeeper.xml").asFile}")
}

// The contracts really ran against ZooKeeper (roadmap R2), as in store/tck. COSID_TEST_ZOOKEEPER_CONNECT points the
// contract tests at a real ZooKeeper (the CI job); without it they run on an in-process curator-test server.
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
