# MongoDB

[`cosid-mongo`](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-mongo) stores segment counters and machine id leases in MongoDB, through the application's synchronous `MongoClient`:

- `MongoIdSegmentDistributor`: segment counters, one document per generator in the `cosid` collection.
- `MongoMachineLeaseStore`: machine id leases, one document per machine id in the `cosid_machine` collection. Lease times come from the server's `localTime` (`hello` command).

The collections are created at startup when missing.

::: warning Synchronous driver only
The reactive classes (`me.ahoo.cosid.mongo.reactive`) were removed in 4.0. Use the synchronous driver, `org.mongodb:mongodb-driver-sync`, which `spring-boot-starter-data-mongodb` includes. With only the reactive driver on the classpath, the starter fails at startup.
:::

## Installation

::: code-group
```kotlin [Gradle (Kotlin)]
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-mongo")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
}
```
```xml [Maven]
<dependencies>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-mongo</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
</dependencies>
```
:::

## Configuration

```yaml
spring:
  mongodb:
    uri: mongodb://localhost:27017
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # for Snowflake IDs and CosIdGenerator
    distributor:
      type: mongo
  segment:
    enabled: true # for segment IDs
    distributor:
      type: mongo
```

| Property | Default | Description |
|----------|---------|-------------|
| `cosid.segment.distributor.mongo.database` | `cosid_db` | Database of the `cosid` collection |
| `cosid.segment.distributor.mongo.timeout` | `5s` | Each segment operation (driver `timeoutMS`); `0` disables it |
| `cosid.machine.distributor.mongo.database` | `cosid_db` | Database of the `cosid_machine` collection |
| `cosid.machine.distributor.mongo.timeout` | `5s` | Each machine id operation (driver `timeoutMS`); must be positive |
| `cosid.segment.distributor.mongo.write-concern`, `cosid.machine.distributor.mongo.write-concern` | the connection string's | Write concern of CosId's writes: `majority`, `w1`, `w2`, `w3`, `acknowledged`, `journaled` or a number of members |

Run MongoDB with journaling (the default) and write concern `majority` on a replica set, so that an acknowledged counter update or machine id claim survives a failover: a write acknowledged by fewer members is rolled back when a member without it becomes primary, and its ids are handed out again. `write-concern` sets it for CosId alone; unset, CosId uses the connection string's (`w=majority`), or the server's default, which is `majority` since MongoDB 5.0. At startup on a replica set, a write concern weaker than `majority` is logged at WARN.
