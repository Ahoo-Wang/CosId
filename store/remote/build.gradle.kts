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

// The remote store (06-stores.md §2.5, 08-remote.md): ranges and slot leases as calls to cosid-server over the JDK
// HttpClient, no third-party dependency. Its TCK runs in server/ against a real server (nothing may depend on server).
plugins {
    id("cosid.library")
}

dependencies {
    api(project(":core"))
}
