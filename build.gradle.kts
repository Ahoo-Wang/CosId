plugins {
    id("io.codearte.nexus-staging")
}

val bomProjects = listOf(
    project(":cosid-bom"),
    project(":cosid-dependencies")
)

val coreProjects = listOf(
    project(":cosid-core")
)

val publishProjects = subprojects
val libraryProjects = publishProjects - bomProjects

ext {
    set("lombokVersion", "1.18.20")
    set("guavaVersion", "30.0-jre")
    set("springBootVersion", "2.4.7")
    set("jmhVersion", "1.29")
    set("junitPioneerVersion", "1.4.2")
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
                name = "sonatypeRepo"
                url = if (version.toString().endsWith("SNAPSHOT"))
                    uri("https://oss.sonatype.org/content/repositories/snapshots")
                else
                    uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")

                credentials {
                    username = getPropertyOf("ossrhUsername")
                    password = getPropertyOf("ossrhPassword")
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

nexusStaging {
    username = getPropertyOf("ossrhUsername")
    password = getPropertyOf("ossrhPassword")
    packageGroup = "me.ahoo"
}

fun getPropertyOf(name: String) = project.properties[name]?.toString()


