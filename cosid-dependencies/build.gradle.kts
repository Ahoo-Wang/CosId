dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${rootProject.ext.get("springBootVersion")}"))
    constraints {
        api("org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}")
        api("com.google.guava:guava:${rootProject.ext.get("guavaVersion")}")
        api("org.junit-pioneer:junit-pioneer:${rootProject.ext.get("junitPioneerVersion")}")
    }
}
