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

// The one Spring Boot starter (D14, 07-runtime-and-spring.md §4.3): dependencies only. Applications add the store
// artifacts they use (cosid-store-jdbc, ...) themselves, as a JPA starter needs a database driver. It depends on
// spring-boot-starter, as Spring Boot's own and third-party starters do: the core starter brings the logging and YAML
// configuration an application expects (R2-build-01: without it, application.yaml did not load).
plugins {
    id("cosid.spring")
}

dependencies {
    api(project(":core"))
    api(project(":spring:autoconfigure"))
    api("org.springframework.boot:spring-boot-starter")
}
