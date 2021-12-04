dependencies {
    api(project(":cosid-core"))
    api("org.apache.shardingsphere:shardingsphere-sharding-api:${rootProject.ext.get("shardingsphereVersion")}")
}
