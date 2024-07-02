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
    api(project(":cosid-core"))
    api("org.springframework.data:spring-data-mongodb")
    compileOnly("org.mongodb:mongodb-driver-sync")
    compileOnly("org.mongodb:mongodb-driver-reactivestreams")
    compileOnly("io.projectreactor:reactor-core")
    testImplementation(project(":cosid-test"))
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mongodb:mongodb-driver-sync")
    testImplementation("io.projectreactor:reactor-core")
    testImplementation("org.mongodb:mongodb-driver-reactivestreams")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
}

val isInCI = null != System.getenv("CI")
if (isInCI) {
    tasks.withType<Test> {
        exclude("me.ahoo.cosid.mongo.MongoReactiveMachineIdDistributorTest")
    }
}