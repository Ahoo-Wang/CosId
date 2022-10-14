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
    java
    jacoco
}

val bomProjects = setOf(
    project(":cosid-bom"),
    project(":cosid-dependencies")
)

val coreProjects = setOf(
    project(":cosid-core")
)
val serverProjects = setOf(
    project(":cosid-example"),
    project(":cosid-example-jdbc"),
    project(":cosid-example-proxy"),
    project(":cosid-example-redis"),
    project(":cosid-example-redis-cosid"),
    project(":cosid-example-zookeeper"),
    project(":cosid-example-shardingsphere"),
    project(":cosid-proxy-server")
)

val testProject = project(":cosid-test")
val publishProjects = subprojects - serverProjects
val libraryProjects = publishProjects - bomProjects

ext {
    set("lombokVersion", "1.18.20")
    set("guavaVersion", "30.0-jre")
    set("springBootVersion", "2.6.9")
    set("springCloudVersion", "2021.0.4")
    set("jmhVersion", "1.34")
    set("junitPioneerVersion", "1.4.2")
    set("hamcrestVersion", "2.2")
    set("mybatisVersion", "3.5.10")
    set("mybatisBootVersion", "2.2.2")
    set("axonBomVersion", "4.5.12")
    set("okhttpVersion", "4.9.3")
    set("coskyVersion", "1.3.20")
    set("shardingsphereVersion", "5.0.0")
    set("testcontainersVersion", "1.17.2")
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
        val DELIMITER = ',';
        val JMH_INCLUDES_KEY = "jmhIncludes"
        val JMH_EXCLUDES_KEY = "jmhExcludes"
        val JMH_THREADS_KEY = "jmhThreads"
        val JMH_MODE_KEY = "jmhMode"

        if (project.hasProperty(JMH_INCLUDES_KEY)) {
            val jmhIncludes = project.properties[JMH_INCLUDES_KEY].toString().split(DELIMITER)
            includes.set(jmhIncludes)
        }
        if (project.hasProperty(JMH_EXCLUDES_KEY)) {
            val jmhExcludes = project.properties[JMH_EXCLUDES_KEY].toString().split(DELIMITER)
            excludes.set(jmhExcludes)
        }

        jmhVersion.set(rootProject.ext.get("jmhVersion").toString())
        warmupIterations.set(1)
        iterations.set(1)
        resultFormat.set("json")

        var jmhMode = listOf(
            "thrpt"
        )
        if (project.hasProperty(JMH_MODE_KEY)) {
            jmhMode = project.properties[JMH_MODE_KEY].toString().split(DELIMITER)
        }
        benchmarkMode.set(jmhMode)
        var jmhThreads = 1
        if (project.hasProperty(JMH_THREADS_KEY)) {
            jmhThreads = Integer.valueOf(project.properties[JMH_THREADS_KEY].toString())
        }
        threads.set(jmhThreads)
        fork.set(1)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        val depLombok = "org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}"
        add("api", platform(project(":cosid-dependencies")))
        add("compileOnly", depLombok)
        add("annotationProcessor", depLombok)
        add("testCompileOnly", depLombok)
        add("testAnnotationProcessor", depLombok)
        add("implementation", "com.google.guava:guava")
        add("implementation", "org.slf4j:slf4j-api")
        add("testImplementation", "ch.qos.logback:logback-classic")
        add("testImplementation", "org.junit.jupiter:junit-jupiter-api")
        add("testImplementation", "org.junit.jupiter:junit-jupiter-params")
        add("testImplementation", "org.junit-pioneer:junit-pioneer")
        add("testImplementation", "org.hamcrest:hamcrest")
        add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine")
        add("jmh", "org.openjdk.jmh:jmh-core:${rootProject.ext.get("jmhVersion")}")
        add("jmh", "org.openjdk.jmh:jmh-generator-annprocess:${rootProject.ext.get("jmhVersion")}")
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

tasks.register<JacocoReport>("codeCoverageReport") {
    executionData(fileTree(project.rootDir.absolutePath).include("**/build/jacoco/*.exec"))
    libraryProjects.forEach {
        dependsOn(it.tasks.test)
        if (testProject != it) {
            sourceSets(it.sourceSets.main.get())
        }
    }
    reports {
        xml.required.set(true)
        html.outputLocation.set(file("${buildDir}/reports/jacoco/report.xml"))
        csv.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/"))
    }
}
