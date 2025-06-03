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
    api(platform(libs.spring.boot.dependencies))
    api(platform(libs.spring.cloud.dependencies))
    api(platform(libs.coapi.bom))
    api(platform(libs.mongodb.driver.bom))
    api(platform(libs.testcontainers.bom))
    constraints {
        api(libs.guava)
        api(libs.mybatis)
        api(libs.mybatis.spring.boot.starter)
        api(libs.springdoc.openapi.starter.webflux.ui)
        api(libs.junit.pioneer)
        api(libs.hamcrest)
        api(libs.jmhCore)
        api(libs.jmh.generator.annprocess)
    }
}
