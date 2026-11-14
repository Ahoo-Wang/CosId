// L14 (#1471): store/redis and store/zookeeper as a plain-Java application gets them: from the published metadata in
// build/repos, with no Spring Boot BOM (nor any other) aligning their versions. The test tools come by their own BOMs,
// which manage nothing but themselves, so the Netty on the test classpath is the one the stores' metadata resolves.
plugins {
    java
}

val cosidVersion = findProperty("cosid.version")?.toString() ?: error("-Pcosid.version=<the version in build/repos>")
val junitVersion = "6.1.3"
val testcontainersVersion = "2.0.5"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    maven {
        url = uri(findProperty("cosid.repo")?.toString() ?: error("-Pcosid.repo=<CosId build>/build/repos"))
    }
    mavenCentral()
}

dependencies {
    testImplementation("me.ahoo.cosid:cosid-store-redis:$cosidVersion")
    testImplementation("me.ahoo.cosid:cosid-store-zookeeper:$cosidVersion")
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(platform("org.testcontainers:testcontainers-bom:$testcontainersVersion"))
    testImplementation("org.testcontainers:testcontainers")
}

// What the test proves holds only on a classpath nothing of Spring aligns: fail before the test otherwise.
val checkNoSpring by tasks.registering {
    val components = configurations.testRuntimeClasspath.map { classpath ->
        classpath.incoming.resolutionResult.allComponents.mapNotNull { it.moduleVersion }.map { "${it.group}:${it.name}:${it.version}" }
    }
    doLast {
        val spring = components.get().filter { it.startsWith("org.springframework") }
        check(spring.isEmpty()) { "A plain consumer resolves Spring artifacts, such as a Boot BOM: $spring" }
        logger.lifecycle("Netty on the test classpath: " + components.get().filter { it.startsWith("io.netty:") }.sorted())
    }
}

tasks.test {
    dependsOn(checkNoSpring)
    useJUnitPlatform()
}
