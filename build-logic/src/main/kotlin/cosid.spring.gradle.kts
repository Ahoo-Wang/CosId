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
// - The Spring Boot BOM manages this project's build classpaths only: it is never published, so an application keeps
//   the versions of its own Boot. Dependencies other projects of this build see carry explicit versions instead.
// - The published metadata requires the versions of the lowest supported Boot (spring-boot-minimum), and
//   checkPublishedMetadata (run by check) fails when a dependency has no version or the BOM leaks into it.
// - The compatibility matrix: -PspringBootVersion=4.0.8 builds and tests on another Boot (an enforced platform, so a
//   higher BOM a dependency brings loses); -PtestJavaVersion (cosid.java) runs the tests on another JDK.
plugins {
    id("cosid.library")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun version(name: String) = libs.findVersion(name).get().requiredVersion

fun Configuration.isBuildClasspath() = isCanBeResolved &&
    (name.endsWith("Classpath", ignoreCase = true) || name.endsWith("AnnotationProcessor") || name == "annotationProcessor")

val springBootVersion = findProperty("springBootVersion")?.toString()
val managedVersions = configurations.create("managedVersions") {
    isCanBeConsumed = false
    isCanBeResolved = false
}
val springBootOverride = configurations.create("springBootOverride") {
    isCanBeConsumed = false
    isCanBeResolved = false
}
configurations.configureEach {
    if (this != managedVersions && this != springBootOverride && name != "minimumVersions" && isBuildClasspath()) {
        extendsFrom(managedVersions, springBootOverride)
    }
}
// What the published metadata requires: what runtimeClasspath declares, at the versions of the lowest supported Boot.
val minimumVersions = configurations.create("minimumVersions") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
afterEvaluate {
    val runtimeClasspath = configurations.getByName("runtimeClasspath")
    minimumVersions.setExtendsFrom(runtimeClasspath.extendsFrom.filter { it != springBootOverride && it != managedVersions })
}
dependencies {
    // -PspringBootVersion replaces the build's Boot rather than competing with it.
    if (springBootVersion == null) {
        managedVersions(platform("org.springframework.boot:spring-boot-dependencies:${version("spring-boot")}"))
    } else {
        springBootOverride(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    }
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

// The published metadata carries a version for every dependency and none of the build's platforms.
val checkPublishedMetadata = tasks.register("checkPublishedMetadata") {
    group = "verification"
    description = "Fails when the published metadata has a dependency without a version or imports the build's Spring Boot BOM."
    val metadata = tasks.named("generateMetadataFileForMavenLibraryPublication")
    val pom = tasks.named("generatePomFileForMavenLibraryPublication")
    dependsOn(metadata, pom)
    val moduleFile = layout.buildDirectory.file("publications/mavenLibrary/module.json")
    val pomFile = layout.buildDirectory.file("publications/mavenLibrary/pom-default.xml")
    val projectName = project.path
    doLast {
        @Suppress("UNCHECKED_CAST")
        val module = groovy.json.JsonSlurper().parse(moduleFile.get().asFile) as Map<String, Any?>
        val problems = mutableListOf<String>()
        @Suppress("UNCHECKED_CAST")
        (module["variants"] as List<Map<String, Any?>>).forEach { variant ->
            @Suppress("UNCHECKED_CAST")
            (variant["dependencies"] as List<Map<String, Any?>>? ?: emptyList()).forEach { dependency ->
                val coordinates = "${dependency["group"]}:${dependency["module"]}"
                if (dependency["version"] == null) {
                    problems += "${variant["name"]}: $coordinates has no version"
                }
                @Suppress("UNCHECKED_CAST")
                val category = (dependency["attributes"] as Map<String, Any?>?)?.get("org.gradle.category")
                if (category == "platform" && dependency["module"] == "spring-boot-dependencies") {
                    problems += "${variant["name"]}: the build platform $coordinates is published"
                }
            }
        }
        val pomText = pomFile.get().asFile.readText()
        if (pomText.contains("<artifactId>spring-boot-dependencies</artifactId>")) {
            problems += "pom: imports the build platform"
        }
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile.get().asFile)
        val dependencies = document.getElementsByTagName("dependency")
        for (index in 0 until dependencies.length) {
            val dependency = dependencies.item(index) as org.w3c.dom.Element
            fun child(name: String) = dependency.getElementsByTagName(name).item(0)?.textContent?.trim()
            if (child("version").isNullOrEmpty()) {
                problems += "pom: ${child("groupId")}:${child("artifactId")} has no version"
            }
        }
        check(problems.isEmpty()) { "Published metadata of $projectName:\n" + problems.joinToString("\n") }
    }
}
tasks.named("check") {
    dependsOn(checkPublishedMetadata)
}
