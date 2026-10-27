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
    // P10 (MongoDB)
    jmh(project(":store:mongo"))
    // R2-Redis: P7 / P10
    jmh(project(":store:redis"))
    // P10 (ZooKeeper): the store and an in-process server.
    jmh(project(":store:zookeeper"))
    jmh(libs.curator.test)
}
