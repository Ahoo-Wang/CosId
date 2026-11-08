# Plain-Java consumer of two stores

L14 (#1471): `store/redis` (Lettuce 7, Netty 4.2) and `store/zookeeper` on one plain-Java classpath, resolved from the
jars and metadata `publishAllPublicationsToProjectBuildRepoRepository` writes to `build/repos`, **outside** the CosId
build: no Spring Boot BOM aligns the third-party versions, as none does for an application without Spring. The test
tools (JUnit, Testcontainers) come by their own BOMs, which manage nothing else; `checkNoSpring` fails the build if
anything of Spring is on the test classpath. The test checks that every Netty artifact has one version, and reserves
through each store (Docker: Redis and ZooKeeper in Testcontainers). CI job `published-jars` runs it; it is no part of
`./gradlew check`.

```bash
./gradlew publishAllPublicationsToProjectBuildRepoRepository
./gradlew -p smoke/plain-stores -Pcosid.version=5.0.1-SNAPSHOT -Pcosid.repo=$PWD/build/repos test
```
