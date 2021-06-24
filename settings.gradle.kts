rootProject.name = "CosId"

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("io.codearte.gradle.nexus:gradle-nexus-staging-plugin:0.30.0")
    }
}

include(":cosid-core")
include(":cosid-bom")
include(":cosid-dependencies")
include(":spring-boot-starter-cosid")
include(":cosid-redis")
include(":cosid-example")
