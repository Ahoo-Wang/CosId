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

rootProject.name = "CosId"

include(":cosid-bom")
include(":cosid-dependencies")
include(":cosid-core")
include(":cosid-spring-boot-starter")
include(":cosid-spring-redis")
include(":cosid-jdbc")
include(":cosid-mybatis")
include(":cosid-jackson")
include(":cosid-shardingsphere")
include(":cosid-zookeeper")
include(":cosid-test")
include(":cosid-proxy")
include(":cosid-proxy-server")
include(":cosid-axon")
include(":cosid-mongo")

include("cosid-example")
project(":cosid-example").projectDir = file("examples/cosid-example")

include("cosid-example-proxy")
project(":cosid-example-proxy").projectDir = file("examples/cosid-example-proxy")

include("cosid-example-redis")
project(":cosid-example-redis").projectDir = file("examples/cosid-example-redis")

include("cosid-example-redis-cosid")
project(":cosid-example-redis-cosid").projectDir = file("examples/cosid-example-redis-cosid")

include("cosid-example-zookeeper")
project(":cosid-example-zookeeper").projectDir = file("examples/cosid-example-zookeeper")

include("cosid-example-jdbc")
project(":cosid-example-jdbc").projectDir = file("examples/cosid-example-jdbc")

include("cosid-example-shardingsphere")
project(":cosid-example-shardingsphere").projectDir = file("examples/cosid-example-shardingsphere")


buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("me.champeau.jmh:jmh-gradle-plugin:0.6.8")
        classpath("io.github.gradle-nexus:publish-plugin:1.1.0")
        classpath("com.github.spotbugs.snom:spotbugs-gradle-plugin:5.0.13")
    }
}
