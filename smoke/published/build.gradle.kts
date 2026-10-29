// R2-build-01/02: the published CosId jars in a Gradle application on Spring Boot 4.1.2, the BOM as a native platform()
// (README.md); -Pcosid.version and -Pcosid.repo (build/repos of the CosId build).
plugins {
    java
    id("org.springframework.boot") version "4.1.2"
}

val cosidVersion = findProperty("cosid.version")?.toString() ?: "5.0.0-SNAPSHOT"

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
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    // As an application has it (logging, YAML); the CosId starter brings Spring Boot's autoconfiguration only.
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-spring-boot-starter:$cosidVersion")
    implementation("me.ahoo.cosid:cosid-store-redis:$cosidVersion")
    implementation("me.ahoo.cosid:cosid-store-mongo:$cosidVersion")
}

tasks.bootJar {
    archiveFileName = "smoke.jar"
}
