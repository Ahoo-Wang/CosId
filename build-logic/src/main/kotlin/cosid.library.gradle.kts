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

import me.ahoo.cosid.gradle.ArtifactIds

// A published library module.
plugins {
    id("cosid.java")
    id("cosid.publish")
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.named<Jar>("jar") {
    // A stable module name for modules without a module-info.java (core has its own).
    manifest {
        attributes("Automatic-Module-Name" to ArtifactIds.moduleName(project.path))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenLibrary") {
            from(components["java"])
            // The versions this build resolved, under the BOM of cosid.java: the BOM itself is never published. For
            // spring/* they are the versions of the lowest Boot supported, the build's (D49).
            versionMapping {
                allVariants {
                    fromResolutionResult()
                }
            }
        }
    }
}

// The published metadata of every library (E-N2): no enforced platform, none of the build's platforms (cosid.java
// puts the Spring Boot BOM on the build classpaths only), and a version for every dependency, unless a published
// platform of the same variant manages it.
val checkPublishedMetadata = tasks.register("checkPublishedMetadata") {
    group = "verification"
    description = "Fails when the published metadata has a dependency without a version, an enforced platform, or the build's Spring Boot BOM."
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
            val variantDependencies = variant["dependencies"] as List<Map<String, Any?>>? ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            fun categoryOf(dependency: Map<String, Any?>) = (dependency["attributes"] as Map<String, Any?>?)?.get("org.gradle.category")
            val managed = variantDependencies.any { categoryOf(it) == "platform" && it["module"] != "spring-boot-dependencies" }
            variantDependencies.forEach { dependency ->
                val coordinates = "${dependency["group"]}:${dependency["module"]}"
                if (dependency["version"] == null && !managed) {
                    problems += "${variant["name"]}: $coordinates has no version"
                }
                val category = categoryOf(dependency)
                if (category == "enforced-platform") {
                    // An enforced platform overrides the consumer's own versions of everything it lists.
                    problems += "${variant["name"]}: the enforced platform $coordinates is published"
                } else if (category == "platform" && dependency["module"] == "spring-boot-dependencies") {
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
        // A dependency without a version is valid Maven when an imported BOM (dependencyManagement) manages it.
        val importsBom = document.getElementsByTagName("dependencyManagement").length > 0
        for (index in 0 until dependencies.length) {
            val dependency = dependencies.item(index) as org.w3c.dom.Element
            fun child(name: String) = dependency.getElementsByTagName(name).item(0)?.textContent?.trim()
            if (child("version").isNullOrEmpty() && !importsBom) {
                problems += "pom: ${child("groupId")}:${child("artifactId")} has no version"
            }
        }
        check(problems.isEmpty()) { "Published metadata of $projectName:\n" + problems.joinToString("\n") }
    }
}
tasks.named("check") {
    dependsOn(checkPublishedMetadata)
}
