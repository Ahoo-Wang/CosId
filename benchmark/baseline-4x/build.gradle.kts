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

// The 4.1.0 baseline (D21, 10-performance-and-parity.md §1.1): the scenarios of benchmark/scenarios.md on the released
// 4.x jars. It shares the package me.ahoo.cosid with 5.x, so it must never depend on a project of this build.
plugins {
    id("cosid.benchmark")
}

dependencies {
    jmh(libs.cosid4.core)
    jmh(libs.cosid4.accessor)
    // P10 (MongoDB): the 4.x Mongo distributor and lease store, on the same driver as 5.x.
    jmh(libs.cosid4.mongo)
    jmh(libs.mongodb.driver.sync)
}
