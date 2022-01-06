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
    id("me.champeau.jmh") version "0.6.4"
}

dependencies {
    api(project(":cosid-core"))
    api("org.springframework.data:spring-data-redis")
    testImplementation("io.lettuce:lettuce-core")
    jmh("org.openjdk.jmh:jmh-core:${rootProject.ext.get("jmhVersion")}")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:${rootProject.ext.get("jmhVersion")}")
}

jmh {
    val DELIMITER = ',';
    val JMH_INCLUDES_KEY = "jmhIncludes"
    val JMH_EXCLUDES_KEY = "jmhExcludes"
    val JMH_THREADS_KEY = "jmhThreads"
    val JMH_MODE_KEY = "jmhMode"

    if (project.hasProperty(JMH_INCLUDES_KEY)) {
        val jmhIncludes = project.properties[JMH_INCLUDES_KEY].toString().split(DELIMITER)
        includes.set(jmhIncludes)
    }
    if (project.hasProperty(JMH_EXCLUDES_KEY)) {
        val jmhExcludes = project.properties[JMH_EXCLUDES_KEY].toString().split(DELIMITER)
        excludes.set(jmhExcludes)
    }

    jmhVersion.set(rootProject.ext.get("jmhVersion").toString())
    warmupIterations.set(1)
    iterations.set(1)
    resultFormat.set("json")

    var jmhMode = listOf(
        "thrpt"
    )
    if (project.hasProperty(JMH_MODE_KEY)) {
        jmhMode = project.properties[JMH_MODE_KEY].toString().split(DELIMITER)
    }
    benchmarkMode.set(jmhMode)
    var jmhThreads = 1
    if (project.hasProperty(JMH_THREADS_KEY)) {
        jmhThreads = Integer.valueOf(project.properties[JMH_THREADS_KEY].toString())
    }
    threads.set(jmhThreads)
    fork.set(1)
}
