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

// The project's own artifacts (02-modules.md §5): add each published module here.
plugins {
    id("cosid.platform")
}

dependencies {
    constraints {
        api(project(":core"))
        api(project(":store:tck"))
        api(project(":store:jdbc"))
        api(project(":store:mongo"))
        api(project(":store:redis"))
        api(project(":store:zookeeper"))
        api(project(":store:remote"))
        api(project(":integrations"))
        api(project(":spring:autoconfigure"))
        api(project(":spring:starter"))
    }
}
