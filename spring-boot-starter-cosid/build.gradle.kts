java {
    registerFeature("redisSupport") {
        usingSourceSet(sourceSets[SourceSet.MAIN_SOURCE_SET_NAME])
        capability(group.toString(), "redis-support", version.toString())
    }
}

dependencies {
    api(project(":cosid-core"))
    "redisSupportImplementation"(project(":cosid-redis"))
    "redisSupportImplementation"("me.ahoo.cosky:cosky-spring-cloud-core")
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.cloud:spring-cloud-commons")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${rootProject.ext.get("springBootVersion")}")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:${rootProject.ext.get("springBootVersion")}")
}

