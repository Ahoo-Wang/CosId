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

description = "CosId test specification module"

java {
    registerFeature("mongoSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "mongo-support", version.toString())
    }
}

dependencies {
    implementation(project(":cosid-core"))
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation("org.hamcrest:hamcrest")
    "mongoSupportImplementation"("org.testcontainers:testcontainers")
    "mongoSupportImplementation"("org.testcontainers:testcontainers-junit-jupiter")
    "mongoSupportImplementation"("org.testcontainers:testcontainers-mongodb")
}
