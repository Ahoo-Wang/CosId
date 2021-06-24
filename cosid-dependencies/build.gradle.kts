dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${rootProject.ext.get("springBootVersion")}"))
    api(platform("org.springframework.cloud:spring-cloud-dependencies:${rootProject.ext.get("springCloudVersion")}"))
    api(platform("me.ahoo.cosky:cosky-bom:${rootProject.ext.get("coskyVersion")}"))
    constraints {
        api("org.projectlombok:lombok:${rootProject.ext.get("lombokVersion")}")
        api("com.google.guava:guava:${rootProject.ext.get("guavaVersion")}")
        api("org.junit-pioneer:junit-pioneer:${rootProject.ext.get("junitPioneerVersion")}")
        api("io.springfox:springfox-boot-starter:${rootProject.ext.get("springfoxVersion")}")
    }
}
