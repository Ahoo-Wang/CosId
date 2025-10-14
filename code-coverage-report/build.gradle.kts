import org.gradle.api.artifacts.Configuration

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

plugins {
    base
    java
    id("jacoco-report-aggregation")
}

@Suppress("UNCHECKED_CAST")
val libraryProjects = rootProject.ext.get("libraryProjects") as Iterable<Project>

dependencies {
    libraryProjects.forEach {
        jacocoAggregation(it)
    }
}

reporting {
    reports {
        val codeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName = "test"
        }
    }
}

tasks.check {
    dependsOn(tasks.named<JacocoReport>("codeCoverageReport"))
}

tasks.register<Javadoc>("aggregateJavadoc") {
    dependsOn(libraryProjects.map { it.tasks.named("compileJava") })
    title = "CosId | 通用、灵活、高性能的分布式 ID 生成器"
    options.header("<a href='${project.properties["website"]}' target='_blank'>GitHub</a>")
    options.destinationDirectory =
        rootProject.layout.buildDirectory
            .dir("aggregatedJavadoc")
            .get()
            .asFile
    libraryProjects.forEach {
        source += it.sourceSets["main"].allJava
    }
    doFirst {
        val allFiles = mutableListOf<File>()
        for (libProject in libraryProjects) {
            val config = libProject.sourceSets["main"].compileClasspath as Configuration
            val resolvedConfig = config.resolvedConfiguration
            allFiles.addAll(resolvedConfig.resolvedArtifacts.map { it.file })
        }
        classpath += project.files(allFiles)
    }
}
