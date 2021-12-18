/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

plugins {
    id("io.github.gradle-nexus.publish-plugin") version ("1.1.0")
}

val bomProjects = listOf(
    project(":cosid-bom"),
    project(":cosid-dependencies")
)

val coreProjects = listOf(
    project(":cosid-core")
)
val serverProjects = listOf(
    project(":cosid-example")
)

val publishProjects = subprojects - serverProjects
val libraryProjects = publishProjects - bomProjects

ext {
    set("lombokVersion", "1.18.20")
    set("guavaVersion", "29.0-jre")
    set("springBootVersion", "2.4.13")
    set("springCloudVersion", "2020.0.5")
    set("springfoxVersion", "3.0.0")
    set("jmhVersion", "1.33")
    set("junitPioneerVersion", "1.4.2")
    set("mybatisVersion", "3.5.7")
    set("mybatisBootVersion", "2.1.4")
    set("coskyVersion", "1.3.10")
    set("shardingsphereVersion", "5.0.0")
    set("libraryProjects", libraryProjects)
}


allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

configure(bomProjects) {
    apply<JavaPlatformPlugin>()
    configure<JavaPlatformExtension> {
        allowDependencies()
    }
}


configure(libraryProjects) {
    apply<JavaLibraryPlugin>()
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        val depLombok = "org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}"
        this.add("api", platform(project(":cosid-dependencies")))
        this.add("compileOnly", depLombok)
        this.add("annotationProcessor", depLombok)
        this.add("testCompileOnly", depLombok)
        this.add("testAnnotationProcessor", depLombok)
        this.add("implementation", "com.google.guava:guava")
        this.add("implementation", "org.slf4j:slf4j-api")
        this.add("testImplementation", "ch.qos.logback:logback-classic")
        this.add("testImplementation", "org.junit.jupiter:junit-jupiter-api")
        this.add("testImplementation", "org.junit.jupiter:junit-jupiter-params")
        this.add("testImplementation", "org.junit-pioneer:junit-pioneer")
        this.add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine")
    }
}

configure(publishProjects) {
    val isBom = bomProjects.contains(this)
    apply<MavenPublishPlugin>()
    apply<SigningPlugin>()
    configure<PublishingExtension> {
        repositories {
            maven {
                name = "projectBuildRepo"
                url = uri(layout.buildDirectory.dir("repos"))
            }
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/Ahoo-Wang/CosId")
                credentials {
                    username = project.findProperty("gitHubPackagesUserName") as String
                    password = project.findProperty("gitHubPackagesToken") as String
                }
            }
        }
        publications {
            val publishName = if (isBom) "mavenBom" else "mavenLibrary"
            val publishComponentName = if (isBom) "javaPlatform" else "java"
            create<MavenPublication>(publishName) {
                from(components[publishComponentName])
                pom {
                    name.set(rootProject.name)
                    description.set(getPropertyOf("description"))
                    url.set(getPropertyOf("website"))
                    issueManagement {
                        system.set("GitHub")
                        url.set(getPropertyOf("issues"))
                    }
                    scm {
                        url.set(getPropertyOf("website"))
                        connection.set(getPropertyOf("vcs"))
                    }
                    licenses {
                        license {
                            name.set(getPropertyOf("license_name"))
                            url.set(getPropertyOf("license_url"))
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("ahoo-wang")
                            name.set("ahoo wang")
                            organization {
                                url.set(getPropertyOf("website"))
                            }
                        }
                    }
                }
            }
        }
    }
    configure<SigningExtension> {
        if (isBom) {
            sign(extensions.getByType(PublishingExtension::class).publications.get("mavenBom"))
        } else {
            sign(extensions.getByType(PublishingExtension::class).publications.get("mavenLibrary"))
        }
    }
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

fun getPropertyOf(name: String) = project.properties[name]?.toString()


