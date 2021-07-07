plugins {
    id("me.champeau.jmh") version "0.6.4"
}

dependencies {
    api("com.google.guava:guava")
    testImplementation("org.junit-pioneer:junit-pioneer")
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
    threads.set(2)
    fork.set(1)
}
