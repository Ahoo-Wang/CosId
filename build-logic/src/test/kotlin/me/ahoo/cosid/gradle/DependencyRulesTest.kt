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

import me.ahoo.cosid.gradle.DependencyRules.Edge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DependencyRulesTest {
    private val projects = listOf(
        ":", ":core", ":bom", ":store", ":store:tck", ":store:jdbc", ":store:redis", ":spring", ":spring:autoconfigure",
        ":spring:starter", ":server", ":integrations", ":extras", ":extras:sharding", ":migration", ":benchmark",
        ":benchmark:baseline-4x", ":benchmark:v5", ":examples", ":examples:segment-jdbc",
    )

    private fun violations(vararg edges: String) = DependencyRules.violations(projects, edges.map { Edge.parse(it) })

    @ParameterizedTest
    @ValueSource(
        strings = [
            ":store:tck->:core",
            ":store:jdbc->:core",
            ":store:jdbc->:store:tck(test)",
            ":core->:store:tck(test)",
            ":integrations->:core",
            ":extras:sharding->:core",
            ":migration->:store:jdbc",
            ":migration->:spring:autoconfigure(test)",
            ":spring:autoconfigure->:store:redis",
            ":spring:autoconfigure->:integrations",
            ":spring:starter->:spring:autoconfigure",
            ":server->:spring:autoconfigure",
            ":server->:store:jdbc",
            ":benchmark:v5->:core",
            ":benchmark:v5->:store:tck",
            ":examples:segment-jdbc->:spring:starter",
        ],
    )
    fun allows(edge: String) {
        assertEquals(emptyList<String>(), violations(edge))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            ":core->:store:jdbc",
            ":core->:spring:autoconfigure",
            ":store:jdbc->:spring:autoconfigure",
            ":store:jdbc->:store:redis",
            ":store:jdbc->:store:tck",
            ":store:tck->:store:jdbc",
            ":integrations->:spring:autoconfigure",
            ":spring:autoconfigure->:server",
            ":spring:starter->:store:jdbc",
            ":extras:sharding->:store:jdbc",
            ":core->:server(test)",
            ":store:jdbc->:migration",
            ":migration->:spring:autoconfigure",
            ":migration->:spring:starter(test)",
            ":store:jdbc->:spring:autoconfigure(test)",
            ":server->:migration",
            ":core->:benchmark:v5(test)",
            ":examples:segment-jdbc->:server",
            ":benchmark:baseline-4x->:core",
            ":benchmark:baseline-4x->:store:tck(test)",
        ],
    )
    fun rejects(edge: String) {
        val problems = violations(edge)
        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems[0].startsWith(Edge.parse(edge).toString()), problems[0])
    }

    @Test
    fun parsesTheProbeForm() {
        assertEquals(Edge(":store:tck", ":core", false), Edge.parse(":store:tck->:core"))
        assertEquals(Edge(":core", ":server", true), Edge.parse(" :core -> :server(test) "))
    }

    @Test
    fun rejectsAProjectWithoutARule() {
        val problems = DependencyRules.violations(projects + ":tools", emptyList())
        assertEquals(1, problems.size, problems.toString())
        assertTrue(problems[0].startsWith(":tools: no dependency rule"), problems[0])
    }
}
