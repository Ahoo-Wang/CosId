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


// The integration adapters (07-runtime-and-spring.md §5, D15): Jackson, MyBatis, Spring Data JDBC, Activiti, Flowable
// and Axon in one module. Every third-party library is compileOnly: an adapter is used only by an application that has
// its library, and the classes of the others are never loaded.
plugins {
    id("cosid.spring")
}

dependencies {
    api(project(":core"))
    implementation(libs.slf4j.api)
    compileOnly("tools.jackson.core:jackson-databind")
    compileOnly(libs.mybatis)
    compileOnly("org.springframework.data:spring-data-jdbc")
    compileOnly(libs.activiti.engine) { isTransitive = false }
    compileOnly(libs.flowable.engine.common) { isTransitive = false }
    compileOnly(libs.axon.messaging) { isTransitive = false }

    testImplementation(project(":store:tck"))
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation(libs.mybatis)
    testImplementation("org.springframework.data:spring-data-jdbc")
    testImplementation(libs.activiti.engine)
    testImplementation(libs.flowable.engine)
    testImplementation(libs.axon.messaging)
    testImplementation(libs.h2)
}
