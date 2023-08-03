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

java {
    registerFeature("springRedisSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "spring-redis-support", version.toString())
    }
    registerFeature("jdbcSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "jdbc-support", version.toString())
    }
    registerFeature("mongoSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "mongo-support", version.toString())
    }
    registerFeature("zookeeperSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "zookeeper-support", version.toString())
    }
    registerFeature("proxySupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "proxy-support", version.toString())
    }
    registerFeature("mybatisSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "mybatis-support", version.toString())
    }
    registerFeature("dataJdbcSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "data-jdbc-support", version.toString())
    }
    registerFeature("cloudSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "cloud-support", version.toString())
    }
    registerFeature("actuatorSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "actuator-support", version.toString())
    }
}

dependencies {
    api(project(":cosid-core"))
    testImplementation(project(mapOf("path" to ":cosid-mongo")))
    testImplementation(project(mapOf("path" to ":cosid-mongo")))

    "springRedisSupportImplementation"(project(":cosid-spring-redis"))
    "springRedisSupportImplementation"("org.springframework.boot:spring-boot-starter-data-redis")

    "jdbcSupportImplementation"(project(":cosid-jdbc"))
    "zookeeperSupportImplementation"(project(":cosid-zookeeper"))

    "proxySupportImplementation"(project(":cosid-proxy"))
    "mongoSupportImplementation"(project(":cosid-mongo"))

    "mybatisSupportImplementation"(project(":cosid-mybatis"))
    "dataJdbcSupportImplementation"(project(":cosid-spring-data-jdbc"))
    api("org.springframework.boot:spring-boot-starter")
    "cloudSupportImplementation"("org.springframework.cloud:spring-cloud-commons")
    "actuatorSupportImplementation"("org.springframework.boot:spring-boot-starter-actuator")
    compileOnly("org.mongodb:mongodb-driver-sync")
    compileOnly("org.mongodb:mongodb-driver-reactivestreams")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    testImplementation(project(":cosid-test"))
    testImplementation("org.mongodb:mongodb-driver-sync")
    testImplementation("org.mongodb:mongodb-driver-reactivestreams")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.apache.curator:curator-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
}
