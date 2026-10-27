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
 * The allowed dependency directions between the projects of this build (docs/architecture/02-modules.md §3).
 *
 * Every project must match a rule, so a new module cannot slip in unchecked. A rule names the projects a project may
 * depend on; `:x:*` matches every project under `:x`. Test scopes may also use [TEST_ONLY] (the store TCK and its
 * in-memory store); no project may depend on `server`, `migration`, `examples` or `benchmark`, since no rule allows it.
 */
object DependencyRules {
    /** A declared dependency of [from] on [to]; [test] is true for test scopes (`test*` configurations). */
    data class Edge(val from: String, val to: String, val test: Boolean) {
        override fun toString() = "$from -> $to" + if (test) " (test)" else ""

        companion object {
            /** Parses `:from->:to` or `:from->:to(test)`, the form of the `cosid.dependencyRules.probe` property. */
            fun parse(text: String): Edge {
                val parts = text.split("->").map { it.trim() }
                require(parts.size == 2) { "Expected ':from->:to' or ':from->:to(test)', got '$text'" }
                val test = parts[1].endsWith("(test)")
                return Edge(parts[0], parts[1].removeSuffix("(test)").trim(), test)
            }
        }
    }

    const val TCK = ":store:tck"
    private val TEST_ONLY = listOf(TCK)
    private const val CORE = ":core"
    private const val STORES = ":store:*"
    private const val SPRING = ":spring:*"
    private const val INTEGRATIONS = ":integrations:*"
    private const val EXTRAS = ":extras:*"

    /** Project pattern → the projects it may depend on in main scopes. An exact path wins over a pattern. */
    val RULES: Map<String, List<String>> = linkedMapOf(
        CORE to emptyList(),
        TCK to listOf(CORE),
        STORES to listOf(CORE),
        // D29: each integration on core and its own library only, never on another integration.
        INTEGRATIONS to listOf(CORE),
        EXTRAS to listOf(CORE),
        ":migration" to listOf(CORE, STORES),
        ":spring:autoconfigure" to listOf(CORE, STORES, INTEGRATIONS),
        ":spring:starter" to listOf(CORE, ":spring:autoconfigure"),
        ":server" to listOf(CORE, STORES, SPRING, INTEGRATIONS),
        ":bom" to emptyList(),
        // The 4.x baseline shares the package me.ahoo.cosid: it must never see a 5.x project (10 §1.1).
        ":benchmark:baseline-4x" to emptyList(),
        ":benchmark:v5" to listOf(CORE, TCK, STORES, SPRING, INTEGRATIONS, EXTRAS),
        ":examples:*" to listOf(CORE, STORES, SPRING, INTEGRATIONS, EXTRAS),
    )

    /**
     * Test-scope-only edges beyond [TEST_ONLY], by project. `:migration` binds its `config` output through the real
     * Spring binding in its tests (R4-C); its main scopes, and so its fat jar, stay free of Spring.
     */
    val TEST_EDGES: Map<String, List<String>> = mapOf(
        ":migration" to listOf(":spring:autoconfigure"),
    )

    /** Projects that only group others and hold no code, such as `:store`. */
    private val CONTAINERS = setOf(":store", ":spring", ":integrations", ":extras", ":benchmark", ":examples")

    fun violations(projects: Collection<String>, edges: Collection<Edge>): List<String> {
        val problems = mutableListOf<String>()
        projects.filter { it != ":" && it !in CONTAINERS && ruleOf(it) == null }
            .forEach { problems += "$it: no dependency rule; add one to DependencyRules.RULES (02-modules.md §3)" }
        for (edge in edges) {
            val allowed = ruleOf(edge.from) ?: continue
            val ok = allowed.any { matches(it, edge.to) && !(it == STORES && edge.to == TCK) } ||
                (edge.test && edge.from != ":benchmark:baseline-4x" && TEST_ONLY.contains(edge.to)) ||
                (edge.test && TEST_EDGES[edge.from].orEmpty().contains(edge.to))
            if (!ok) {
                problems += "$edge: not allowed by the dependency rules (02-modules.md §3)"
            }
        }
        return problems
    }

    private fun ruleOf(project: String): List<String>? =
        RULES[project] ?: RULES.entries.firstOrNull { it.key.endsWith(":*") && matches(it.key, project) }?.value

    private fun matches(pattern: String, project: String): Boolean =
        if (pattern.endsWith(":*")) project.startsWith(pattern.removeSuffix("*")) else pattern == project
}
