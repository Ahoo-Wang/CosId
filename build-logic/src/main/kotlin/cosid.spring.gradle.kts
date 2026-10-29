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

// A published library built on Spring Boot (spring/*, integrations, later server; 02-modules.md §5).
//
// - The Spring Boot BOM manages this project's build classpaths only (cosid.java): it is never published, so an
//   application keeps the versions of its own Boot.
// - The published metadata requires the versions of the lowest supported Boot (spring-boot-minimum);
//   checkPublishedMetadata (cosid.library, run by check) fails when a dependency has no version or the BOM leaks into it.
// - The compatibility matrix: -PspringBootVersion=4.0.9 builds and tests on another Boot (an enforced platform, so a
//   higher BOM a dependency brings loses); -PtestJavaVersion (cosid.java) runs the tests on another JDK.
plugins {
    id("cosid.library")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun version(name: String) = libs.findVersion(name).get().requiredVersion

val springBootVersion = findProperty("springBootVersion")?.toString()
// What the published metadata requires: what runtimeClasspath declares, at the versions of the lowest supported Boot.
val minimumVersions = configurations.create("minimumVersions") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
afterEvaluate {
    val runtimeClasspath = configurations.getByName("runtimeClasspath")
    minimumVersions.setExtendsFrom(runtimeClasspath.extendsFrom.filter { it.name != "managedVersions" })
}
dependencies {
    minimumVersions(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:${version("spring-boot-minimum")}"))
}
if (springBootVersion != null) {
    tasks.withType<Test>().configureEach {
        // Tests that depend on the Spring Boot version check they really run on it.
        environment("COSID_TEST_SPRING_BOOT_VERSION", springBootVersion)
    }
}

publishing {
    publications.named<MavenPublication>("mavenLibrary") {
        versionMapping {
            allVariants {
                fromResolutionOf("minimumVersions")
            }
        }
    }
}
