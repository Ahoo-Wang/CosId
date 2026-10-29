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

// jmhJar keeps the first copy of a path, and each store module has its own
// META-INF/services/me.ahoo.cosid.store.StoreProvider: unmerged, ServiceLoader in the jar finds the providers of one
// module only (the release gate of 2026-10 lost its Redis, MySQL and MongoDB chains so). The services files of the JMH
// classpath are merged into one per interface, which replaces every copy in the jar.
fun servicesOf(files: Iterable<File>): Map<String, Set<String>> {
    val services = sortedMapOf<String, MutableSet<String>>()
    fun add(name: String, text: String) {
        text.lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
            .forEach { services.getOrPut(name) { linkedSetOf() }.add(it) }
    }
    for (file in files) {
        if (file.isDirectory) {
            file.resolve("META-INF/services").listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { add(it.name, it.readText()) }
        } else if (file.name.endsWith(".jar") && file.isFile) {
            java.util.zip.ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("META-INF/services/") && it.name.count { c -> c == '/' } == 2 }
                    .forEach { add(it.name.removePrefix("META-INF/services/"), zip.getInputStream(it).bufferedReader().readText()) }
            }
        }
    }
    return services
}

val jmhClasspath: FileCollection = configurations.getByName("jmhRuntimeClasspath") + the<SourceSetContainer>()["jmh"].output
val jmhServices = layout.buildDirectory.dir("jmh-services")
val mergeJmhServices = tasks.register("mergeJmhServices") {
    description = "Merges the META-INF/services files of the JMH classpath, for jmhJar."
    inputs.files(jmhClasspath)
    outputs.dir(jmhServices)
    doLast {
        val dir = jmhServices.get().asFile.resolve("META-INF/services")
        dir.deleteRecursively()
        dir.mkdirs()
        servicesOf(jmhClasspath).forEach { (name, providers) -> dir.resolve(name).writeText(providers.joinToString("\n", postfix = "\n")) }
    }
}
tasks.named<Jar>("jmhJar") {
    dependsOn(mergeJmhServices)
    val merged = jmhServices.get().asFile.toPath()
    eachFile {
        if (path.startsWith("META-INF/services/") && !file.toPath().startsWith(merged)) {
            exclude()
        }
    }
    from(jmhServices)
}

// The jar as the release gate runs it (10 §1.3): every services file holds the providers of every module, and
// ServiceLoader loads and instantiates each provider of a CosId interface. Benchmarks that need a store cannot run on
// CI's Check job, so this is what catches a jar that would fail their setup.
tasks.register<Exec>("checkJmhJar") {
    description = "Checks the services of the JMH jar: merged, and every CosId provider loads."
    group = "verification"
    val jar = tasks.named<Jar>("jmhJar").flatMap { it.archiveFile }
    inputs.file(jar)
    inputs.files(jmhClasspath)
    val checker = rootProject.file("benchmark/JmhJarCheck.java")
    inputs.file(checker)
    val launcher = javaToolchains.launcherFor(java.toolchain)
    doFirst {
        val expected = servicesOf(jmhClasspath)
        val actual = servicesOf(listOf(jar.get().asFile))
        val wrong = expected.filter { (name, providers) -> actual[name] != providers }
        if (wrong.isNotEmpty()) {
            throw GradleException("The JMH jar lacks providers: " + wrong.map { (name, providers) ->
                "$name: ${providers - actual[name].orEmpty()}" }.joinToString("; "))
        }
        executable = launcher.get().executablePath.asFile.absolutePath
        args(listOf("-cp", jar.get().asFile.absolutePath, checker.absolutePath) +
            expected.filterKeys { it.startsWith("me.ahoo.cosid.") }.map { (name, providers) -> "$name=${providers.size}" })
    }
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
