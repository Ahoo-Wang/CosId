plugins {
    id("me.champeau.jmh") version "0.6.4"
}

dependencies {
    api(project(":cosid-core"))
    api("org.apache.shardingsphere:shardingsphere-sharding-api:${rootProject.ext.get("shardingsphereVersion")}")
    testImplementation("org.junit-pioneer:junit-pioneer")
    testImplementation("org.apache.shardingsphere:shardingsphere-sharding-core:${rootProject.ext.get("shardingsphereVersion")}")
    jmh("org.openjdk.jmh:jmh-core:${rootProject.ext.get("jmhVersion")}")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:${rootProject.ext.get("jmhVersion")}")
}

jmh {
    jmhVersion.set(rootProject.ext.get("jmhVersion").toString())
    warmupIterations.set(1)
    iterations.set(1)
    resultFormat.set("json")
    benchmarkMode.set(listOf(
        "thrpt"
    ))
    threads.set(1)
    fork.set(1)
}
