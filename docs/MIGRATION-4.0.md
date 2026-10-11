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

### Thread-local grouped ids

`GroupedAccessor` is removed, and generating an id no longer publishes its group on the current thread. The group travels with the id instead:

| 3.x | 4.0 |
|---|---|
| `segmentId.generate()` then `GroupedAccessor.get()` | `segmentId.generateGrouped()` returns a `GroupedId` (group and value) |
| `groupedPrefixConverter.asString(id)` (group from the thread) | `converter.asString(id, group)` or `converter.asString(groupedId)`; `asString(long)` throws `UnsupportedOperationException` |
| `stringSegmentId.generateAsString()` | unchanged: a string segment id whose converter has a grouped prefix passes the group of the segment |

`SegmentId.generateGrouped()` has no default implementation any more: a custom `SegmentId` must implement it. A custom converter decorator should override `asString(long, GroupedKey)` and pass the group on. With the starter, `converter.group-prefix.enabled` on a segment id without `group.by` fails at startup.

_Still to be filled in as the changes land: the old machine id distributors._

### APIs deprecated for removal in 3.x

| Removed | Use instead |
|---|---|
| `AbstractSnowflakeId.nextTime()` (no longer called since 3.4) | override `getCurrentTime()` to change the clock |
| `CosIdMachineIdLifecycle(MachineIdGuarder, MachineIdDistributor)` | `CosIdMachineIdLifecycle(MachineIdGuarder, MachineIdDistributor, GuardDistribute)` |

### Test segment distributors

`IdSegmentDistributor.Atomic` and `IdSegmentDistributor.Mock` moved out of `cosid-core` to the `cosid-test` artifact as `me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor` and `me.ahoo.cosid.test.segment.MockIdSegmentDistributor`, with the same constructors.

### Sharding algorithms moved to `cosid-sharding`

`me.ahoo.cosid.sharding` (`ModCycle`, `IntervalTimeline`, `CachedSharding`, ...) moved from `cosid-core` to the new `me.ahoo.cosid:cosid-sharding` artifact, with the same package and class names. Add the dependency where you use them; the BOM manages its version. Downstream integrations need it too, such as the CosId sharding algorithms in Apache ShardingSphere (`shardingsphere-sharding-cosid`). Guava is now a dependency of `cosid-sharding` only.

### Accessor and annotations moved to `cosid-accessor`

`me.ahoo.cosid.accessor` (id field accessors, `CosIdAccessorRegistry`, the field parsers) and `me.ahoo.cosid.annotation` (`@CosId`, `@AccessorFactory`) moved from `cosid-core` to the new `me.ahoo.cosid:cosid-accessor` artifact, with the same package and class names. The starter, `cosid-mybatis` and `cosid-spring-data-jdbc` depend on it, so applications using them need no change; add it only where you use `@CosId` or the accessors without them.

## Changed defaults

| Property | 3.x | 4.0 |
|---|---|---|
| `cosid.jdbc.enabled` | `true` | `false` |
| `cosid.actuator.delete.enabled` | `true` | `false` |

Set them to `true` to keep the 3.x behavior. **Spring Data JDBC users must set `cosid.jdbc.enabled=true`**: with it off, CosId no longer fills the `@Id` property (or `@CosId` fields) of Spring Data JDBC entities, and an INFO at startup names those entities. With `cosid.actuator.delete.enabled` off, the `cosid` endpoint is read-only (`CosIdReadOnlyEndpoint`), and the 3.4 startup warning is gone.

## API changes

### String-only generators are not `IdGenerator`s

`CosIdGenerator` (and its decorators) and `UuidGenerator` now implement only `StringIdGenerator`, which `IdGenerator` extends; `StringIdGenerator` is `Statistical`. Their `generate()` and `idConverter()`, which always threw `UnsupportedOperationException`, are gone. `IdGeneratorProvider` holds `StringIdGenerator`s and has typed lookups:

| Injection point or call | 3.x | 4.0 |
|---|---|---|
| a Spring `IdGenerator` injection point | could resolve to the `CosIdGenerator` bean | never does: inject `CosIdGenerator` or `StringIdGenerator` for it |
| `provider.get(name)` | `Optional<IdGenerator>` | `Optional<StringIdGenerator>`; `get(name, IdGenerator.class)` for a `long` generator |
| `provider.getRequired(name).generate()` | | `provider.getRequired(name, IdGenerator.class).generate()` |
| `provider.getRequired(name).generateAsString()` | | unchanged |
| `provider.set(name, ...)`, `remove`, `entries()`, `getAll()` | `IdGenerator` | `StringIdGenerator` |
| `provider.getShare()`, `setShare(...)` | `IdGenerator` | unchanged; `set(SHARE, ...)` with a string-only generator throws `IllegalArgumentException` |
| `UuidGenerator.INSTANCE` | `IdGenerator` | `StringIdGenerator` |
| `LazyIdGenerator.tryGet(...)` | `IdGenerator` | `StringIdGenerator`; `generate()` and `getActual()` on a string-only generator throw `CosIdException`, `generateAsString()` works |

A typed lookup of a generator with another type throws `IllegalArgumentException` naming both types. `@CosId` fields of type `String` keep working with the `cosid` generator; `long` and `int` fields need an `IdGenerator`. A `long` or `int` field bound to a string-only generator fails when its accessor is registered, at the end of startup with the starter, or at the latest on the first fill, with a `CosIdException` naming the field and the generator.

_To be filled in: `SnowflakeLayout`, the new `cosid-sharding` and `cosid-accessor` modules, the configuration property model._

The API diff against 3.4 is reported on every pull request to `main` (the **API Diff** workflow).
