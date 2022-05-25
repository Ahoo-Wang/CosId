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
    api(platform("org.springframework.boot:spring-boot-dependencies:${rootProject.ext.get("springBootVersion")}"))
    api(platform("org.springframework.cloud:spring-cloud-dependencies:${rootProject.ext.get("springCloudVersion")}"))
    api(platform("com.squareup.okhttp3:okhttp-bom:${rootProject.ext.get("okhttpVersion")}"))
    api(platform("me.ahoo.cosky:cosky-bom:${rootProject.ext.get("coskyVersion")}"))
    constraints {
        api("org.axonframework:axon-messaging:${rootProject.ext.get("axonVersion")}")
        api("org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}")
        api("org.mybatis:mybatis:${rootProject.ext.get("mybatisVersion")}")
        api("org.mybatis.spring.boot:mybatis-spring-boot-starter:${rootProject.ext.get("mybatisBootVersion")}")
        api("com.google.guava:guava:${rootProject.ext.get("guavaVersion")}")
        api("org.junit-pioneer:junit-pioneer:${rootProject.ext.get("junitPioneerVersion")}")
        api("org.hamcrest:hamcrest:${rootProject.ext.get("hamcrestVersion")}")
        api("io.springfox:springfox-boot-starter:${rootProject.ext.get("springfoxVersion")}")
    }
}
