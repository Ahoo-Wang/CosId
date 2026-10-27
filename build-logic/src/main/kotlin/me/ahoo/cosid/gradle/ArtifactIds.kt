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

/**
 * The published coordinates derived from a project's directory (D2, D11; docs/architecture/02-modules.md §2):
 * `"cosid-" + path.replace('/', '-')`, with the modules under `spring/` published as `cosid-spring-boot-...` and those under `extras/` and `integrations/` as
 * `cosid-...`.
 */
object ArtifactIds {
    /** The artifactId of the project at [projectPath], a Gradle path such as `:store:jdbc`. */
    fun of(projectPath: String): String {
        val path = projectPath.removePrefix(":").replace(':', '/')
        require(path.isNotEmpty()) { "The root project is not published" }
        return when {
            path.startsWith("spring/") -> "cosid-spring-boot-" + path.removePrefix("spring/").replace('/', '-')
            // 02 §1 publishes extras/sharding as cosid-sharding, the 4.x artifact name.
            path.startsWith("extras/") -> "cosid-" + path.removePrefix("extras/").replace('/', '-')
            // D29: integrations/axon is cosid-axon, the 4.x coordinates.
            path.startsWith("integrations/") -> "cosid-" + path.removePrefix("integrations/").replace('/', '-')
            else -> "cosid-" + path.replace('/', '-')
        }
    }

    /** The JPMS module name of the project at [projectPath], such as `me.ahoo.cosid.store.jdbc`. */
    fun moduleName(projectPath: String): String =
        "me.ahoo.cosid." + of(projectPath).removePrefix("cosid-").replace('-', '.')
}
