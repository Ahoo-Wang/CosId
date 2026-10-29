# Published-jar smoke test

R2-build-01/02: an application built **outside** this Gradle build, from the jars `publishAllPublicationsToProjectBuildRepoRepository`
writes to `build/repos`, on Spring Boot 4.1.2, the lowest Boot supported (D49), twice: with Maven
(`spring-boot-starter-parent`) and with Gradle (a native `platform()` of the Boot BOM). It starts the starter with the
Redis and MongoDB stores as an application gets them, leases a slot from Redis, issues snowflake ids on it and segment
ids from MongoDB, and prints the store drivers each build resolved. CI job `published-jars` runs it; it is no part of
`./gradlew check`.

```bash
./gradlew publishAllPublicationsToProjectBuildRepoRepository
mvn -f smoke/published/pom.xml -Dcosid.version=5.0.0-SNAPSHOT -Dcosid.repo=file://$PWD/build/repos package
./gradlew -p smoke/published -Pcosid.version=5.0.0-SNAPSHOT -Pcosid.repo=$PWD/build/repos bootJar
REDIS_URI=redis://localhost:6379 MONGO_URI=mongodb://localhost:27017 java -jar smoke/published/target/smoke.jar
```
