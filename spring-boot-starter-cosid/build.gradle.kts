dependencies {
    api(project(":cosid-core"))
    implementation(project(":cosid-redis"))
    implementation("me.ahoo.cosky:cosky-spring-cloud-core")
    api("org.springframework.boot:spring-boot-starter")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${rootProject.ext.get("springBootVersion")}")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:${rootProject.ext.get("springBootVersion")}")
}

