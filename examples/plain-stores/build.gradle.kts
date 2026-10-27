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

// A plain-Java consumer of two stores (L14): no Spring, so no Boot BOM aligns the third-party versions; the classpath
// is what Gradle resolves from the published metadata of store/redis and store/zookeeper. Test only, not published.
plugins {
    id("cosid.java")
}

dependencies {
    testImplementation(project(":store:redis"))
    testImplementation(project(":store:zookeeper"))
    testImplementation(libs.testcontainers)
}
