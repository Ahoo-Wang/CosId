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

plugins {
    id("io.github.gradle-nexus.publish-plugin")
    id("me.champeau.jmh")
    `java-library`
    jacoco
}

val dependenciesProject = project(":cosid-dependencies")
val bomProjects = setOf(
    project(":cosid-bom"),
    dependenciesProject,
)

val coreProjects = setOf(
    project(":cosid-core")
)
val serverProjects = setOf(
    project(":cosid-example-proxy"),
    project(":cosid-example-redis"),
    project(":cosid-example-redis-cosid"),
    project(":cosid-example-zookeeper"),
    project(":cosid-proxy-server")
)

val testProject = project(":cosid-test")
val codeCoverageReportProject = project(":code-coverage-report")
val publishProjects = subprojects - serverProjects - codeCoverageReportProject
val libraryProjects = publishProjects - bomProjects

ext {
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
    apply<CheckstylePlugin>()
    configure<CheckstyleExtension> {
        toolVersion = "9.2.1"
    }
    apply<com.github.spotbugs.snom.SpotBugsPlugin>()
    configure<com.github.spotbugs.snom.SpotBugsExtension> {
        excludeFilter.set(file("${rootDir}/config/spotbugs/exclude.xml"))
    }
    apply<JacocoPlugin>()
    apply<JavaLibraryPlugin>()
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
        withJavadocJar()
        withSourcesJar()
    }
    apply<me.champeau.jmh.JMHPlugin>()
    configure<me.champeau.jmh.JmhParameters> {
        val delimiter = ',';
        val jmhIncludesKey = "jmhIncludes"
        val jmhExcludesKey = "jmhExcludes"
        val jmhThreadsKey = "jmhThreads"
        val jmhModeKey = "jmhMode"

        if (project.hasProperty(jmhIncludesKey)) {
            val jmhIncludes = project.properties[jmhIncludesKey].toString().split(delimiter)
            includes.set(jmhIncludes)
        }
        if (project.hasProperty(jmhExcludesKey)) {
            val jmhExcludes = project.properties[jmhExcludesKey].toString().split(delimiter)
            excludes.set(jmhExcludes)
        }

        warmupIterations.set(1)
        iterations.set(1)
        resultFormat.set("json")

        var jmhMode = listOf(
            "thrpt"
        )
        if (project.hasProperty(jmhModeKey)) {
            jmhMode = project.properties[jmhModeKey].toString().split(delimiter)
        }
        benchmarkMode.set(jmhMode)
        var jmhThreads = 1
        if (project.hasProperty(jmhThreadsKey)) {
            jmhThreads = Integer.valueOf(project.properties[jmhThreadsKey].toString())
        }
        threads.set(jmhThreads)
        fork.set(1)
        jvmArgs.set(listOf("-Dlogback.configurationFile=${rootProject.rootDir}/config/logback-jmh.xml"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // fix logging missing code for JacocoPlugin
        jvmArgs = listOf("-Dlogback.configurationFile=${rootProject.rootDir}/config/logback.xml")
    }

    dependencies {
        api(platform(dependenciesProject))
        annotationProcessor(platform(dependenciesProject))
        testAnnotationProcessor(platform(dependenciesProject))
        jmh(platform(dependenciesProject))
        compileOnly("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")
        testCompileOnly("org.projectlombok:lombok")
        testAnnotationProcessor("org.projectlombok:lombok")
        implementation("com.google.guava:guava")
        implementation("org.slf4j:slf4j-api")
        testImplementation("ch.qos.logback:logback-classic")
        testImplementation("org.junit.jupiter:junit-jupiter-api")
        testImplementation("org.junit.jupiter:junit-jupiter-params")
        testImplementation("org.junit-pioneer:junit-pioneer")
        testImplementation("org.hamcrest:hamcrest")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
        jmh("org.openjdk.jmh:jmh-core")
        jmh("org.openjdk.jmh:jmh-generator-annprocess")
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
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
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
        val isInCI = null != System.getenv("CI");
        if (isInCI) {
            val signingKeyId = System.getenv("SIGNING_KEYID")
            val signingKey = System.getenv("SIGNING_SECRETKEY")
            val signingPassword = System.getenv("SIGNING_PASSWORD")
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        }

        if (isBom) {
            sign(extensions.getByType(PublishingExtension::class).publications.get("mavenBom"))
        } else {
            sign(extensions.getByType(PublishingExtension::class).publications.get("mavenLibrary"))
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            username.set(System.getenv("MAVEN_USERNAME"))
            password.set(System.getenv("MAVEN_PASSWORD"))
        }
    }
}

fun getPropertyOf(name: String) = project.properties[name]?.toString()
