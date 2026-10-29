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


// cosid-activiti (07-runtime-and-spring.md §5, D29): Activiti's id generator on a CosId generator.
plugins {
    id("cosid.spring")
}

dependencies {
    api(project(":core"))
    api(libs.activiti.engine)
    // R2-dep-01 (D45): Activiti 7.0.0.SR1, the latest on Maven Central, brings MyBatis 3.4.2 (GHSA-qq48-m4jx-xqh8, fixed in
    // 3.5.6). A direct dependency, so that Maven's nearest-wins and Gradle alike resolve the patched one, the latest 3.5
    // (3.6 breaks Activiti 7); the Activiti engine test runs on it. Remove once an Activiti after 7.0.0.SR1 brings
    // MyBatis >= 3.5.6.
    api(libs.mybatis.activiti)
    testImplementation(project(":store:tck"))
    testRuntimeOnly(libs.h2)
}
