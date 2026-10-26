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

import me.ahoo.cosid.gradle.ArtifactIds

// A published library module.
plugins {
    id("cosid.java")
    id("cosid.publish")
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.named<Jar>("jar") {
    // A stable module name for modules without a module-info.java (core has its own).
    manifest {
        attributes("Automatic-Module-Name" to ArtifactIds.moduleName(project.path))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenLibrary") {
            from(components["java"])
        }
    }
}
