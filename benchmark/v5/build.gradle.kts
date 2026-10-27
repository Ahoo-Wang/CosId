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

// The same scenarios as benchmark/baseline-4x on the 5.x modules (10-performance-and-parity.md §1.1).
plugins {
    id("cosid.benchmark")
}

dependencies {
    jmh(project(":core"))
    jmh(project(":store:tck"))
    // P7 / P10 (R2-JDBC): a real MySQL, COSID_TEST_MYSQL_URL (benchmark/scenarios.md).
    jmh(project(":store:jdbc"))
    jmh(libs.mysql.connector)
    jmh(libs.hikari)
    // P10 (MongoDB)
    jmh(project(":store:mongo"))
    // R2-Redis: P7 / P10
    jmh(project(":store:redis"))
    // ZooKeeper brings Netty 4.1 epoll, Lettuce 7 needs 4.2: on Linux Lettuce picks epoll and fails on the 4.1 classes.
    // Align every Netty artifact, as the Spring Boot BOM does for applications.
    jmh(platform(libs.netty.bom))
    // P10 (ZooKeeper): the store and an in-process server.
    jmh(project(":store:zookeeper"))
    jmh(libs.curator.test)
    // P11: Spring context start to first id, both sides on the Spring Boot of spring/* (spring-boot-starter brings the
    // same logging to both).
    jmh(project(":spring:starter"))
    jmh(libs.spring.boot.starter)
}
