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

// The JDBC store (06-stores.md §2.1): RangeStore + SlotLeaseStore over a DataSource, MySQL, MariaDB, PostgreSQL,
// SQL Server and H2. Depends on core and the JDK's java.sql only; the drivers are the application's choice.
plugins {
    id("cosid.library")
}

dependencies {
    api(project(":core"))
    testImplementation(project(":store:tck"))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.mariadb)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mssqlserver)
    testImplementation(libs.hikari)
    testRuntimeOnly(libs.mysql.connector)
    testRuntimeOnly(libs.mariadb.client)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mssql.jdbc)
    testImplementation(libs.h2)
}

// The contracts really ran (roadmap R2), per dialect: the report of each dialect's contract tests is checked on its
// own, so a job whose database was missing fails instead of passing on H2 alone. H2 always runs; the CI job of this
// module names every dialect: -Pcosid.store.jdbc.dialects=h2,mysql,mariadb,postgresql,sqlserver.
val testClassPrefixes = mapOf("h2" to "H2", "mysql" to "MySql", "mariadb" to "MariaDb", "postgresql" to "Postgres", "sqlserver" to "SqlServer")
val checkedDialects = (findProperty("cosid.store.jdbc.dialects")?.toString() ?: "h2").split(',').map { it.trim() }.filter { it.isNotEmpty() }
val checkContractsRan by tasks.registering {
    description = "Checks from the JUnit reports that every store contract case ran, per dialect."
    group = "verification"
}
for (dialect in checkedDialects) {
    val prefix = requireNotNull(testClassPrefixes[dialect]) { "Unknown dialect '$dialect', one of ${testClassPrefixes.keys}" }
    val reports = tasks.register<Sync>("contractReports-$dialect") {
        dependsOn(tasks.test)
        from(layout.buildDirectory.dir("test-results/test")) { include("TEST-me.ahoo.cosid.store.jdbc.$prefix*.xml") }
        into(layout.buildDirectory.dir("contract-reports/$dialect"))
    }
    val check = tasks.register<JavaExec>("checkContractsRan-$dialect") {
        description = "Checks that every store contract case ran on $dialect."
        group = "verification"
        dependsOn(reports)
        val dir = layout.buildDirectory.dir("contract-reports/$dialect")
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("me.ahoo.cosid.store.tck.ContractsRan")
        argumentProviders.add(CommandLineArgumentProvider { listOf(dir.get().asFile.absolutePath) })
    }
    checkContractsRan { dependsOn(check) }
}
tasks.check {
    dependsOn(checkContractsRan)
}
