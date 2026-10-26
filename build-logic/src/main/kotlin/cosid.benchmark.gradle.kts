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

// A JMH benchmark project (benchmark/*, not published). JMH is applied here only (02-modules.md §5).
//
// Profiles (-PjmhProfile, see benchmark/scenarios.md): smoke (default; short, for development and PR smoke runs) and
// release (the release gate of 10-performance-and-parity.md §1.3). -PjmhIncludes=regex,regex narrows the scenarios.
plugins {
    id("cosid.java")
    id("me.champeau.jmh")
}

val libs = the<VersionCatalogsExtension>().named("libs")

jmh {
    jmhVersion.set(libs.findVersion("jmh").get().requiredVersion)
    // The plugin concatenates list entries into one argument, so a comma list becomes one alternation regex.
    findProperty("jmhIncludes")?.toString()?.let { includes.set(listOf(it.split(',').joinToString("|"))) }
    findProperty("jmhExcludes")?.toString()?.let { excludes.set(listOf(it.split(',').joinToString("|"))) }
    when (val profile = findProperty("jmhProfile")?.toString() ?: "smoke") {
        "smoke" -> {
            fork.set(1)
            warmupIterations.set(2)
            warmup.set("1s")
            iterations.set(3)
            timeOnIteration.set("1s")
        }
        "release" -> {
            fork.set(3)
            warmupIterations.set(5)
            warmup.set("2s")
            iterations.set(5)
            timeOnIteration.set("2s")
        }
        else -> throw GradleException("Unknown -PjmhProfile=$profile: use smoke or release")
    }
    // Allocation per operation (gc.alloc.rate.norm) is a gate of its own (10 §1.3).
    profilers.set(listOf("gc"))
    resultFormat.set("JSON")
    jvmArgs.set(listOf("-Dlogback.configurationFile=${rootProject.rootDir}/config/logback-jmh.xml"))
}

// The generated JMH harness is not ours to lint.
tasks.matching { it.name == "spotbugsJmh" }.configureEach { enabled = false }

// benchmark/run-smoke runs the JMH jars itself, on the toolchain's java this prints.
tasks.register("printJmhJava") {
    description = "Prints the java executable of the Java toolchain, for benchmark/run-smoke."
    val launcher = javaToolchains.launcherFor(java.toolchain)
    doLast {
        println(launcher.get().executablePath.asFile.absolutePath)
    }
}
