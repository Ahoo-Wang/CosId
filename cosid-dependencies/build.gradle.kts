
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

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:2.7.9"))
    api(platform("org.springframework.cloud:spring-cloud-dependencies:2021.0.5"))
    api(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
    api(platform("org.testcontainers:testcontainers-bom:1.17.6"))
    constraints {
        api("org.projectlombok:lombok:1.18.26")
        api("org.mybatis:mybatis:3.5.11")
        api("org.mybatis.spring.boot:mybatis-spring-boot-starter:2.3.0")
        api("com.google.guava:guava:30.0-jre")
        api("org.junit-pioneer:junit-pioneer:1.9.1")
        api("org.hamcrest:hamcrest:2.2")
        api("org.apache.shardingsphere:shardingsphere-sharding-api:5.2.1")
        api("org.apache.shardingsphere:shardingsphere-sharding-core:5.2.1")
        api("org.apache.shardingsphere:shardingsphere-jdbc-core-spring-boot-starter:5.2.1")
        api("org.openjdk.jmh:jmh-core:1.36")
        api("org.openjdk.jmh:jmh-generator-annprocess:1.36")
    }
}
