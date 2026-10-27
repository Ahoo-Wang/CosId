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

dependencies {
    testImplementation(platform(library("junit-bom")))
    testImplementation(library("junit-jupiter"))
    testImplementation(library("hamcrest"))
    testRuntimeOnly(library("junit-platform-launcher"))
    testRuntimeOnly(library("logback-classic"))
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
    retry {
        if (isInCI) {
            maxRetries.set(2)
            maxFailures.set(20)
        }
        failOnPassedAfterRetry.set(true)
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
