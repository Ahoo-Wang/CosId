# CosId-Mongo Module

[cosid-mongo](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-mongo) provides support for **MongoDB**. Implements:

- `MongoMachineLeaseStore`: The machine ID lease store for the **Snowflake algorithm** (`SnowflakeId`), used through `LeaseStoreMachineIdDistributor`.
- `IdSegmentDistributor`: As the segment distributor for **segment algorithm** (`SegmentId`).

::: warning Reactive driver support was removed in 4.0
The reactive classes (`me.ahoo.cosid.mongo.reactive`), which only blocked on the reactive driver, were removed in 4.0. Use the synchronous driver (`org.mongodb:mongodb-driver-sync`, included in `spring-boot-starter-data-mongodb`). With only the reactive driver on the classpath, the starter fails at startup.
:::

## Installation

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("me.ahoo.cosid:cosid-mongo:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
            <version>${springboot.version}</version>
        </dependency>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-mongo</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

## Configuration Example

```yaml {4,10,14}
spring:
  data:
    mongodb:
      uri:  # Mongo distributor directly depends on spring-data-mongodb, which can omit additional configuration.
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # Optional, needs to be set to true when using Snowflake algorithm
    distributor:
      type: mongo
  segment:
    enabled: true # Optional, needs to be set to true when using segment algorithm
    distributor:
      type: mongo
```