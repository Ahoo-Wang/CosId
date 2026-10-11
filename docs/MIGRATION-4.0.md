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

_To be filled in as the changes land: MongoDB reactive support, the thread-local grouped path (`GroupedAccessor`), the old machine id distributors, and the APIs deprecated for removal in 3.x._

### APIs deprecated for removal in 3.x

| Removed | Use instead |
|---|---|
| `AbstractSnowflakeId.nextTime()` (no longer called since 3.4) | override `getCurrentTime()` to change the clock |
| `CosIdMachineIdLifecycle(MachineIdGuarder, MachineIdDistributor)` | `CosIdMachineIdLifecycle(MachineIdGuarder, MachineIdDistributor, GuardDistribute)` |

## Changed defaults

| Property | 3.x | 4.0 |
|---|---|---|
| `cosid.jdbc.enabled` | `true` | `false` |
| `cosid.actuator.delete.enabled` | `true` | `false` |

## API changes

_To be filled in: `StringIdGenerator` and the `IdGeneratorProvider` injection points, `SnowflakeLayout`, the new `cosid-sharding` and `cosid-accessor` modules, the configuration property model._

The API diff against 3.4 is reported on every pull request to `main` (the **API Diff** workflow).
