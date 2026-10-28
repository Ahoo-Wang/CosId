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
import me.ahoo.cosid.gradle.CheckDependencyRules
import org.gradle.api.artifacts.ProjectDependency

// Applied to the root project: the build-wide checks.

val checkDependencyRules = tasks.register<CheckDependencyRules>("checkDependencyRules") {
    group = "verification"
    description = "Fails when a project dependency breaks the dependency direction rules (02-modules.md §3)."
    projects.set(provider { rootProject.allprojects.map { it.path } })
    edges.set(provider {
        val declared = rootProject.allprojects.flatMap { project ->
            project.configurations.flatMap { configuration ->
                val test = configuration.name.startsWith("test")
                configuration.dependencies.withType(ProjectDependency::class.java).map {
                    "${project.path}->${it.path}" + if (test) "(test)" else ""
                }
            }
        }
        // -Pcosid.dependencyRules.probe=:core->:server adds a made-up dependency: the documented negative check.
        val probe = findProperty("cosid.dependencyRules.probe")?.toString()
        (declared + listOfNotNull(probe)).distinct()
    })
}

// -Pcosid.test.skipProjects (cosid.java): a misspelt path would leave a project untested without a word.
findProperty("cosid.test.skipProjects")?.toString()?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach {
    requireNotNull(rootProject.findProject(it)) { "cosid.test.skipProjects: no project $it" }
}

// The published libraries: every project with cosid.library, known once all projects are configured.
val publishedLibraries = provider {
    rootProject.subprojects.filter { it.pluginManager.hasPlugin("cosid.library") }.sortedBy { it.path }
}

// E-N3: the BOM lists every published library (and nothing else of this build), read from the POM it publishes.
val checkBom = tasks.register("checkBom") {
    group = "verification"
    description = "Fails when the BOM does not list every published module."
    // By path: the root project is configured before :bom, so its tasks do not exist yet.
    dependsOn(":bom:generatePomFileForMavenBomPublication")
    val pomFile = project(":bom").layout.buildDirectory.file("publications/mavenBom/pom-default.xml")
    val expected = publishedLibraries.map { libraries -> libraries.map { ArtifactIds.of(it.path) }.toSortedSet() }
    val group = project.group.toString()
    doLast {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile.get().asFile)
        val dependencies = document.getElementsByTagName("dependency")
        val listed = (0 until dependencies.length).map { dependencies.item(it) as org.w3c.dom.Element }
            .filter { it.getElementsByTagName("groupId").item(0)?.textContent?.trim() == group }
            .map { it.getElementsByTagName("artifactId").item(0).textContent.trim() }
            .toSortedSet()
        val missing = expected.get() - listed
        val extra = listed - expected.get()
        check(missing.isEmpty() && extra.isEmpty()) {
            "bom/build.gradle.kts must list every published module (02-modules.md §5)" +
                (if (missing.isEmpty()) "" else "\n  missing: ${missing.joinToString()}") +
                (if (extra.isEmpty()) "" else "\n  not published: ${extra.joinToString()}")
        }
    }
}

// E-M2: the documentation site's Javadoc, one directory per published module named by its artifactId (D11) under an
// index page; document-deploy.yml copies build/javadoc-site into the site.
val javadocSite = tasks.register<Sync>("javadocSite") {
    group = "documentation"
    description = "Collects the Javadoc of every published module into build/javadoc-site/<artifactId>."
    val site = layout.buildDirectory.dir("javadoc-site")
    into(site)
    val names = publishedLibraries.map { libraries -> libraries.map { ArtifactIds.of(it.path) } }
    doLast {
        val links = names.get().joinToString("") { "<li><a href=\"$it/index.html\">$it</a></li>" }
        site.get().file("index.html").asFile.apply {
            writeText("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>CosId JavaDoc</title></head>" +
                "<body><h1>CosId JavaDoc</h1><ul>$links</ul></body></html>\n")
        }
    }
}
gradle.projectsEvaluated {
    publishedLibraries.get().forEach { library ->
        javadocSite.configure {
            from(library.tasks.named("javadoc")) { into(ArtifactIds.of(library.path)) }
        }
    }
}

tasks.register("check") {
    group = "verification"
    description = "Runs every check of the build, the dependency rules and build-logic's own tests among them."
    dependsOn(checkDependencyRules)
    dependsOn(checkBom)
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}
