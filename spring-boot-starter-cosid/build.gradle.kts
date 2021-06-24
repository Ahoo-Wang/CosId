dependencies {
    api(project(":cosid-core"))
    implementation(project(":cosid-redis"))
    api("org.springframework.boot:spring-boot-starter")
    api("me.ahoo.cosky:cosky-spring-cloud-core")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${rootProject.ext.get("springBootVersion")}")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:${rootProject.ext.get("springBootVersion")}")
}

