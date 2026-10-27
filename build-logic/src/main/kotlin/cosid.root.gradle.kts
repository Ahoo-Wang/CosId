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

tasks.register("check") {
    group = "verification"
    description = "Runs every check of the build, the dependency rules and build-logic's own tests among them."
    dependsOn(checkDependencyRules)
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}
