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
    api("org.apache.curator:curator-recipes")
    jmh("org.openjdk.jmh:jmh-core:${rootProject.ext.get("jmhVersion")}")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:${rootProject.ext.get("jmhVersion")}")
}

jmh {

    if (project.hasProperty("jmhIncludes")) {
        val jmhIncludes =  project.properties["jmhIncludes"].toString().split(',')
        includes.set(jmhIncludes)
    }
    if (project.hasProperty("jmhExcludes")) {
        val jmhExcludes =  project.properties["jmhExcludes"].toString().split(',')
        excludes.set(jmhExcludes)
    }

    jmhVersion.set(rootProject.ext.get("jmhVersion").toString())
    warmupIterations.set(1)
    iterations.set(1)
    resultFormat.set("json")
    benchmarkMode.set(listOf(
        "thrpt"
    ))
//    threads.set(40)
    fork.set(1)
}
