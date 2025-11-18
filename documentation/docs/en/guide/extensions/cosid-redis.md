# CosId-Redis Module

[cosid-spring-redis](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-spring-redis) module provides support for *Redis*. Implements:

- `MachineIdDistributor`: As the machine ID distributor for **Snowflake algorithm** (`SnowflakeId`).
- `IdSegmentDistributor`: As the segment distributor for **segment algorithm** (`SegmentId`).

## Installation

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("me.ahoo.cosid:cosid-spring-redis:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
            <version>${springboot.version}</version>
        </dependency>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-spring-redis</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

## Configuration Example

[CosId-Example-Redis](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-redis)

```yaml {4,10,14}
spring:
  data:
    redis:
      host: localhost # Redis distributor directly depends on spring-data-redis, which can omit additional configuration.
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # Optional, needs to be set to true when using Snowflake algorithm
    distributor:
      type: redis
  segment:
    enabled: true # Optional, needs to be set to true when using segment algorithm
    distributor:
      type: redis
```


