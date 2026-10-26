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

package me.ahoo.cosid.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Fails the build when a declared project dependency breaks [DependencyRules]. */
abstract class CheckDependencyRules : DefaultTask() {
    /** Every project path of the build. */
    @get:Input
    abstract val projects: ListProperty<String>

    /** Every declared project dependency, as `:from->:to` or `:from->:to(test)`. */
    @get:Input
    abstract val edges: ListProperty<String>

    @TaskAction
    fun check() {
        val problems = DependencyRules.violations(projects.get(), edges.get().map { DependencyRules.Edge.parse(it) })
        if (problems.isNotEmpty()) {
            throw GradleException("Dependency rule violations:\n  " + problems.joinToString("\n  "))
        }
        logger.info("Checked {} project dependencies against the dependency rules", edges.get().size)
    }
}
