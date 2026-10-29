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

import org.gradle.api.tasks.testing.logging.TestExceptionFormat

// Every Java project: Java 21, -parameters, Checkstyle + SpotBugs + JaCoCo, JUnit 5 (02-modules.md §5). No Lombok (D13).
plugins {
    `java-library`
    checkstyle
    jacoco
    id("com.github.spotbugs")
    id("org.gradle.test-retry")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun version(name: String) = libs.findVersion(name).get().requiredVersion
fun library(name: String) = libs.findLibrary(name).get()
val isInCI = System.getenv("CI") != null

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(version("java")))
    }
}

checkstyle {
    toolVersion = version("checkstyle")
    configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
    maxWarnings = 0
}

spotbugs {
    toolVersion.set(version("spotbugs-tool"))
    excludeFilter.set(rootProject.layout.projectDirectory.file("config/spotbugs/exclude.xml"))
}

tasks.withType<JavaCompile>().configureEach {
    // Spring Boot reads endpoint parameter names (CosIdEndpoint): keep -parameters.
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        docEncoding = "UTF-8"
        charSet = "UTF-8"
        addBooleanOption("Xdoclint:all,-missing", true)
    }
}

// D45: the Spring Boot BOM manages the versions of every build classpath, never the published metadata (a library
// publishes the versions it resolved: cosid.library, cosid.spring). -PspringBootVersion replaces it rather than
// competing with it: an enforced platform, so a higher BOM a dependency brings loses (the compatibility matrix).
// A project with cosid.bootBom=false in its gradle.properties takes none (benchmark/baseline-4x: its 4.x jars keep the
// versions they bring).
fun Configuration.isBuildClasspath() = isCanBeResolved &&
    (name.endsWith("Classpath", ignoreCase = true) || name.endsWith("AnnotationProcessor") || name == "annotationProcessor")

if (findProperty("cosid.bootBom")?.toString() != "false") {
    val springBootVersion = findProperty("springBootVersion")?.toString()
    val managedVersions = configurations.create("managedVersions") {
        isCanBeConsumed = false
        isCanBeResolved = false
    }
    configurations.configureEach {
        if (this != managedVersions && name != "minimumVersions" && isBuildClasspath()) {
            extendsFrom(managedVersions)
        }
    }
    dependencies {
        val bom = "org.springframework.boot:spring-boot-dependencies"
        managedVersions(if (springBootVersion == null) platform("$bom:${version("spring-boot")}") else enforcedPlatform("$bom:$springBootVersion"))
    }
}

dependencies {
    testImplementation(library("junit-jupiter"))
    testImplementation(library("hamcrest"))
    testRuntimeOnly(library("junit-platform-launcher"))
    testRuntimeOnly(library("logback-classic"))
    constraints {
        // Checkstyle 14.3.0 brings Guava 33.7.1 (a security advisory); until a Checkstyle after 14.3.0 brings >= 33.7.2.
        "checkstyle"("com.google.guava:guava:${version("guava")}")
    }
}

// -PtestJavaVersion=25 runs the tests on another JDK than the one that compiles them (02-modules.md §5).
val testJavaVersion = findProperty("testJavaVersion")?.toString()
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
    jvmArgs("-Dlogback.configurationFile=${rootProject.rootDir}/config/logback.xml")
    if (testJavaVersion != null) {
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(testJavaVersion)) })
        // Tests that depend on the Java version check they really run on it.
        environment("COSID_TEST_JAVA_VERSION", testJavaVersion)
    }
    // -Pcosid.test.exclude=*.ReplicaWaitTest,… leaves test classes out (the stability workflow).
    findProperty("cosid.test.exclude")?.toString()?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach {
        filter.excludeTestsMatching(it)
    }
    retry {
        // -Pcosid.test.retries=0 turns the CI retries off: the stability workflow counts every failure.
        if (isInCI) {
            maxRetries.set(findProperty("cosid.test.retries")?.toString()?.toInt() ?: 2)
            maxFailures.set(20)
        }
        failOnPassedAfterRetry.set(true)
    }
}

// -Pcosid.test.skipProjects=:store:redis,:server,… (L5): CI's check job leaves these projects' tests, and the contract
// checks on their reports, to the CI jobs of their own, so their containers start once per CI run. The root project
// rejects a path that does not exist.
val skipTests = findProperty("cosid.test.skipProjects")?.toString().orEmpty().split(',').map { it.trim() }
if (path in skipTests) {
    tasks.withType<Test>().configureEach { enabled = false }
    tasks.matching { it.name.startsWith("checkContractsRan") || it.name.startsWith("contractReports-") }.configureEach {
        enabled = false
    }
}

tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required.set(true)
    }
}
tasks.named("check") {
    dependsOn(tasks.withType<JacocoReport>())
}
