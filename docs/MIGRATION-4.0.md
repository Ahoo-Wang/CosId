# Migrating from 3.x to 4.0

> Work in progress: 4.0 is being built on `main` (see [ROADMAP.md](ROADMAP.md)). Each change adds its section here as it lands.

## Requirements

- Java 17 or later.
- Spring Boot 4.x.

## Rolling upgrade

3.x and 4.0 instances can share a namespace during the upgrade: 4.0 reads and writes both lease formats. Finish the upgrade before removing 3.x instances, and keep host clocks in sync with the store.

- **JDBC**: run the `ALTER TABLE` for the new lease columns before starting 4.0 instances; 4.0 fails at startup and prints the statement when the columns are missing.
- **Proxy**: 4.0 servers keep the v1 API for 3.x clients (deprecated).

## Removed

### MongoDB reactive support

`me.ahoo.cosid.mongo.reactive` (`MongoReactiveIdSegmentDistributorFactory`, `MongoReactiveMachineCollection`, the reactive initializers and `BlockingAdapter`) is removed: it only blocked on the reactive driver. Use the sync driver, `org.mongodb:mongodb-driver-sync`, with the classes in `me.ahoo.cosid.mongo`; the data format is the same. With the starter, selecting MongoDB while only the reactive driver is on the classpath fails at startup with a message pointing here.

_Still to be filled in as the changes land: the thread-local grouped path (`GroupedAccessor`), the old machine id distributors, and the APIs deprecated for removal in 3.x._

### Accessor and annotations moved to `cosid-accessor`

`me.ahoo.cosid.accessor` (id field accessors, `CosIdAccessorRegistry`, the field parsers) and `me.ahoo.cosid.annotation` (`@CosId`, `@AccessorFactory`) moved from `cosid-core` to the new `me.ahoo.cosid:cosid-accessor` artifact, with the same package and class names. The starter, `cosid-mybatis` and `cosid-spring-data-jdbc` depend on it, so applications using them need no change; add it only where you use `@CosId` or the accessors without them.

## Changed defaults

| Property | 3.x | 4.0 |
|---|---|---|
| `cosid.jdbc.enabled` | `true` | `false` |
| `cosid.actuator.delete.enabled` | `true` | `false` |

## API changes

_To be filled in: `StringIdGenerator` and the `IdGeneratorProvider` injection points, `SnowflakeLayout`, the new `cosid-sharding` and `cosid-accessor` modules, the configuration property model._

The API diff against 3.4 is reported on every pull request to `main` (the **API Diff** workflow).
