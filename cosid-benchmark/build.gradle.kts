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
    `java-library`
    id("me.champeau.jmh") version "0.7.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenLocal()
    maven { url = uri("https://repo.spring.io/milestone") }
    mavenCentral()
}

dependencies {
    implementation("me.ahoo.cosid:cosid-jdbc:2.2.5")
    implementation("me.ahoo.cosid:cosid-test:2.2.5")
    testImplementation("com.zaxxer:HikariCP:5.0.1")
    testImplementation("mysql:mysql-connector-java:8.0.33")
    /**
     * WARNING：中央仓库没有找到美团官方提供的Jar!!!
     * git clone https://github.com/Meituan-Dianping/Leaf
     * mvn install -Dmaven.test.skip=true
     */
    testImplementation("com.sankuai.inf.leaf:leaf-core:1.0.1")
    /**
     * WARNING：中央仓库没有找到滴滴官方提供的Jar!!!
     * git clone https://github.com/didi/tinyid
     * mvn install -Dmaven.test.skip=true
     */
//    testImplementation("com.xiaoju.uemc.tinyid:tinyid-client:0.1.0-SNAPSHOT")

    jmh("org.openjdk.jmh:jmh-core:1.36")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.36")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
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

    jmhVersion.set("1.36")
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

tasks.withType<Test> {
    useJUnitPlatform()
}
