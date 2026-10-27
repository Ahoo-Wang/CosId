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

// Maven publishing and signing; the artifactId comes from the project's directory (D11, 02-modules.md §2).
plugins {
    `maven-publish`
    signing
}

// Not named artifactId: inside a MavenPublication block that name is the publication's own property.
val publishedArtifactId = ArtifactIds.of(project.path)
val isInCI = System.getenv("CI") != null
fun property(name: String) = rootProject.findProperty(name)?.toString()

pluginManager.withPlugin("base") {
    the<BasePluginExtension>().archivesName.set(publishedArtifactId)
}

publishing {
    repositories {
        maven {
            name = "projectBuildRepo"
            url = uri(rootProject.layout.buildDirectory.dir("repos"))
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Ahoo-Wang/CosId")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications.withType<MavenPublication>().configureEach {
        artifactId = publishedArtifactId
        pom {
            name.set(publishedArtifactId)
            description.set(property("description"))
            url.set(property("website"))
            issueManagement {
                system.set("GitHub")
                url.set(property("issues"))
            }
            scm {
                url.set(property("website"))
                connection.set(property("vcs"))
            }
            licenses {
                license {
                    name.set(property("license_name"))
                    url.set(property("license_url"))
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("ahoo-wang")
                    name.set("ahoo wang")
                    organization {
                        url.set(property("website"))
                    }
                }
            }
        }
    }
}

signing {
    if (isInCI) {
        useInMemoryPgpKeys(System.getenv("SIGNING_KEYID"), System.getenv("SIGNING_SECRETKEY"), System.getenv("SIGNING_PASSWORD"))
    }
    isRequired = isInCI
    sign(publishing.publications)
}
