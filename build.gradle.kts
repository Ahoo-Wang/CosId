import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.testretry.TestRetryPlugin

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
    alias(libs.plugins.test.retry)
    alias(libs.plugins.publish)
    alias(libs.plugins.jmh)
    alias(libs.plugins.spotbugs)
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

val testProjects = setOf(project(":cosid-test"), project(":cosid-mod-test"))
// Built and tested like a library, but holds only tests and benchmarks: not published, not in the BOM.
val unpublishedProjects = setOf(project(":cosid-mod-test"))
val codeCoverageReportProject = project(":code-coverage-report")
val libraryProjects = subprojects - serverProjects - codeCoverageReportProject - bomProjects
val publishedLibraryProjects = libraryProjects - unpublishedProjects
val publishProjects = publishedLibraryProjects + bomProjects
val isInCI = null != System.getenv("CI")
ext.set("libraryProjects", libraryProjects)
ext.set("publishedLibraryProjects", publishedLibraryProjects)

// The configurations the build resolves its own classpaths from: compile, runtime, annotation processing and the JMH
// benchmarks (`jmh`). The build's versions (cosid-dependencies, -PspringBootVersion) manage these, never what is published.
fun Configuration.isBuildClasspath() = isCanBeResolved
    && (name.endsWith("Classpath", ignoreCase = true) || name.endsWith("AnnotationProcessor") || name == "annotationProcessor"
    || name == "jmh")

allprojects {
    repositories {
        mavenLocal()
        maven { url = uri("https://repo.spring.io/milestone") }
        mavenCentral()
    }
    // -PtestJavaVersion=21 runs the tests on another JDK than the one that compiles them (17), as the CI matrix does.
    val testJavaVersion = findProperty("testJavaVersion")?.toString()
    if (testJavaVersion != null) {
        plugins.withType<JavaPlugin> {
            val toolchains = extensions.getByType<JavaToolchainService>()
            tasks.withType<Test>().configureEach {
                javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(testJavaVersion)) })
                // Tests that depend on the Java version check they really run on it.
                environment("COSID_TEST_JAVA_VERSION", testJavaVersion)
            }
        }
    }
    // -PspringBootVersion=4.0.8 builds and tests against another Spring Boot 4.x, as the CI matrix does. A plain
    // platform would lose to a higher Boot BOM brought by a dependency (coapi's metadata brings its own), so the
    // build's classpaths enforce it. Not published: minimumVersions, which maps the published versions, is left alone.
    val springBootVersion = findProperty("springBootVersion")?.toString()
    if (springBootVersion != null) {
        plugins.withType<JavaPlugin> {
            val springBootOverride = configurations.create("springBootOverride") {
                isCanBeConsumed = false
                isCanBeResolved = false
            }
            dependencies {
                springBootOverride(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
            }
            configurations.configureEach {
                if (this != springBootOverride && isBuildClasspath()) {
                    extendsFrom(springBootOverride)
                }
            }
            tasks.withType<Test>().configureEach {
                // Tests that depend on the Spring Boot version check they really run on it.
                environment("COSID_TEST_SPRING_BOOT_VERSION", springBootVersion)
            }
        }
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
            languageVersion.set(JavaLanguageVersion.of(17))
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
    apply<TestRetryPlugin>()
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
        }
        jvmArgs =
            listOf(
                // fix logging missing code for JacocoPlugin
                "-Dlogback.configurationFile=${rootProject.rootDir}/config/logback.xml",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.lang=ALL-UNNAMED"
            )
        retry {
            if (isInCI) {
                maxRetries = 2
                maxFailures = 20
            }
            failOnPassedAfterRetry = true
        }
    }

    // SpringBoot 3.2.0: fix Failed to extract parameter names for CosIdEndpoint
    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-parameters"))
        options.encoding = "UTF-8"
    }
    tasks.withType<Javadoc> {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).apply {
            docEncoding = "UTF-8"
            charSet = "UTF-8"
        }
    }
    // A stable module name for the module path, such as me.ahoo.cosid.core or me.ahoo.cosid.spring.boot.starter.
    tasks.named<Jar>("jar") {
        manifest {
            attributes("Automatic-Module-Name" to "me.ahoo.cosid." + project.name.removePrefix("cosid-").replace('-', '.'))
        }
    }

    // The versions of cosid-dependencies (the Spring Boot BOM among them) manage the build's classpaths, but are not
    // published: the published metadata carries each direct dependency's version, so an application on another Spring
    // Boot 4.x keeps its own versions instead of being moved to those CosId builds with.
    val managedVersions = configurations.create("managedVersions") {
        isCanBeConsumed = false
        isCanBeResolved = false
    }
    configurations.configureEach {
        if (this != managedVersions && isBuildClasspath()) {
            extendsFrom(managedVersions)
        }
    }
    // What the published metadata requires: the versions of the lowest supported Spring Boot (the CI matrix tests it), so
    // that an application on any supported Boot resolves its own. It extends what runtimeClasspath extends (the
    // optional features' configurations among them) but -PspringBootVersion's, which must not reach the published versions.
    val minimumVersions = configurations.create("minimumVersions") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    afterEvaluate {
        val runtimeClasspath = configurations.getByName("runtimeClasspath")
        minimumVersions.setExtendsFrom(runtimeClasspath.extendsFrom.filter { it.name != "springBootOverride" })
    }
    dependencies {
        managedVersions(platform(dependenciesProject))
        minimumVersions(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:${rootProject.libs.versions.spring.boot.minimum.get()}"))
        compileOnly("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")
        testCompileOnly("org.projectlombok:lombok")
        testAnnotationProcessor("org.projectlombok:lombok")
        // @ThreadSafe and @Immutable document thread safety; consumers do not need them.
        compileOnly(rootProject.libs.error.prone.annotations)
        testCompileOnly(rootProject.libs.error.prone.annotations)
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
            maven {
                name = "LinYiPackages"
                url = uri(project.properties["linyiPackageReleaseUrl"].toString())
                credentials {
                    username = project.properties["linyiPackageUsername"]?.toString()
                    password = project.properties["linyiPackagePwd"]?.toString()
                }
            }
        }
        publications {
            val publishName = if (isBom) "mavenBom" else "mavenLibrary"
            val publishComponentName = if (isBom) "javaPlatform" else "java"
            create<MavenPublication>(publishName) {
                from(components[publishComponentName])
                if (!isBom) {
                    // Versions the build resolved, since the managing platform is not published.
                    versionMapping {
                        allVariants {
                            fromResolutionOf("minimumVersions")
                        }
                    }
                }
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

// The published metadata carries a version for every dependency and none of the build's platforms: run by check.
configure(publishedLibraryProjects) {
    val checkPublishedMetadata = tasks.register("checkPublishedMetadata") {
        val metadata = tasks.named("generateMetadataFileForMavenLibraryPublication")
        val pom = tasks.named("generatePomFileForMavenLibraryPublication")
        dependsOn(metadata, pom)
        val moduleFile = layout.buildDirectory.file("publications/mavenLibrary/module.json")
        val pomFile = layout.buildDirectory.file("publications/mavenLibrary/pom-default.xml")
        doLast {
            @Suppress("UNCHECKED_CAST")
            val module = groovy.json.JsonSlurper().parse(moduleFile.get().asFile) as Map<String, Any?>
            val problems = mutableListOf<String>()
            (module["variants"] as List<Map<String, Any?>>).forEach { variant ->
                (variant["dependencies"] as List<Map<String, Any?>>? ?: emptyList()).forEach { dependency ->
                    val coordinates = "${dependency["group"]}:${dependency["module"]}"
                    if (dependency["version"] == null) {
                        problems += "${variant["name"]}: $coordinates has no version"
                    }
                    val category = (dependency["attributes"] as Map<String, Any?>?)?.get("org.gradle.category")
                    if (category == "platform" && (dependency["group"] == "me.ahoo.cosid" || dependency["module"] == "spring-boot-dependencies")) {
                        problems += "${variant["name"]}: the build platform $coordinates is published"
                    }
                }
            }
            val pomText = pomFile.get().asFile.readText()
            if (pomText.contains("<artifactId>cosid-dependencies</artifactId>") || pomText.contains("<artifactId>spring-boot-dependencies</artifactId>")) {
                problems += "pom: imports the build platform"
            }
            val pomDocument = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile.get().asFile)
            val pomDependencies = pomDocument.getElementsByTagName("dependency")
            for (index in 0 until pomDependencies.length) {
                val dependency = pomDependencies.item(index) as org.w3c.dom.Element
                fun child(name: String) = dependency.getElementsByTagName(name).item(0)?.textContent?.trim()
                if (child("version").isNullOrEmpty()) {
                    problems += "pom: ${child("groupId")}:${child("artifactId")} has no version"
                }
            }
            check(problems.isEmpty()) { "Published metadata of ${project.name}:\n" + problems.joinToString("\n") }
        }
    }
    tasks.named("check") {
        dependsOn(checkPublishedMetadata)
    }
}

nexusPublishing {
    this.repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            username.set(System.getenv("SONATYPE_USERNAME"))
            password.set(System.getenv("SONATYPE_PASSWORD"))
        }
    }
}

fun getPropertyOf(name: String) = project.properties[name]?.toString()
