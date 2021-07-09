/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

java {
    registerFeature("redisSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "redis-support", version.toString())
    }
    registerFeature("jdbcSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "jdbc-support", version.toString())
    }
}

dependencies {
    api(project(":cosid-core"))
    "redisSupportImplementation"(project(":cosid-redis"))
    "redisSupportImplementation"("me.ahoo.cosky:cosky-spring-cloud-core")
    "jdbcSupportImplementation"(project(":cosid-jdbc"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.cloud:spring-cloud-commons")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${rootProject.ext.get("springBootVersion")}")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:${rootProject.ext.get("springBootVersion")}")
}

