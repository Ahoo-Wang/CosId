# Migrating from 4.x to 5.0

5.0 keeps no compatibility code (D1): not the API, not the configuration, not the store formats. The one bridge is
the offline tool **`cosid-migration`**. It reads the 4.x data, writes the 5.0 data so that new ids can never collide
with ids 4.x issued, and converts the configuration.

::: danger No mixed fleets
4.x and 5.0 **never run in the same namespace**, not even for a moment. Every 4.x instance of the namespace stops
before the migration, and 5.0 starts after it. This is not a rolling upgrade.
:::

## What the tool guarantees

| # | Constraint | How |
|---|---|---|
| M1 | segments: 5.0's first range lies past everything 4.x reserved | per key, `next = lastMaxId + 1 + gap` |
| M2 | time-based ids: 5.0's first timestamp lies after every 4.x timestamp | every slot of the namespace gets `next_floor = W` |
| M3 | 5.0 and 4.x time-based ids never meet in one column | the 4.x layout and epoch are kept (D16); `plan` checks custom ones |

`W = max(T_apply, T4 + 1s) + sourceSkew`: `T_apply` is the target store's clock when `apply` writes, `T4` the largest
timestamp in the 4.x machine records, `sourceSkew` (10s) the clock skew 4.x allowed. After the migration the first
time-based id waits until the clock passes W, at most about `sourceSkew`.

## Get the tool

`cosid-migration.jar` is an executable fat jar (Java 21), attached to the release, or built with
`./gradlew :migration:shadowJar` (`migration/build/libs/cosid-migration.jar`).

```bash
java -jar cosid-migration.jar --help
```

Source and target may be different store types (Redis → JDBC, MongoDB → ZooKeeper, …):

| Location | 4.x data read | 5.0 data written |
|---|---|---|
| `jdbc:<url>`, options `user`, `password` | `cosid(name, last_max_id)`, `cosid_machine` | `cosid_range`, `cosid_slot`, `cosid_namespace` (`initSchema=true` creates them) |
| `redis://` / `rediss://`, option `cluster=true` | `cosid:{ns.name}.adder`, the `cosid:{ns}:*` machine keys | `cosid:{key}`, `cosid:{ns}:meta`, `:slot`, `:free` |
| `mongodb://`, option `database` | `cosid`, `cosid_machine` | `cosid_range`, `cosid_namespace`, `cosid_slot` |
| `zookeeper://host:port[/chroot]` | `/cosid/{ns}.{name}`, `/cosid/{ns}/…` | `/cosid/range/{key}`, `/cosid/slot/{ns}/…` |

Passwords go in `--from-option password=…` / `--to-option password=…`, or in `COSID_MIGRATION_FROM_PASSWORD` /
`COSID_MIGRATION_TO_PASSWORD`, on every command. They are never written to `plan.json`.

## The runbook

### 1. Stop every 4.x instance

Stop **every** 4.x instance of the namespace: segment-only services too, and Manual or StatefulSet instances, which
leave no machine record behind. The tool cannot prove they are all stopped, so it protects you in three ways: you
confirm it (`--confirm-stopped`), it watches for changes (the quiet period), and it seals the sources (step 3).

### 2. Plan (read-only)

```bash
java -jar cosid-migration.jar plan \
  --from redis://redis-4x:6379 \
  --to jdbc:mysql://db/cosid --to-option user=cosid \
  --namespace shop \
  --layout-config application.yaml     # the 4.x configuration: layouts, group patterns, the share
```

Read the report. It lists every key with its new key and value, the keys `NOT MIGRATED` and why, the W estimate, the
layout checks and the warnings. Fix what it asks for and plan again:

- `--rename old=new`: a 4.x name that is not a valid 5.0 generator name (no `/`), or a name you want changed. The 4.x
  share `__share__` becomes `default`.
- `--group-pattern name=yyyy-MM-dd`: a grouped segment whose group key is not a 5.0 preset (`yyyy`, `yyyyMM`,
  `yyyyMMdd`). Its keys are reformatted to the preset of the same granularity. **Every period is migrated, past ones
  included**, because 4.x used the server's default zone.
- `--slot-bits` (the 4.x `machine-bit`, 10 by default), `--tolerance` (1s): the slot parameters of the 5.0 namespace.

Key mapping: `{ns}.{name}` → `{ns}/{name}`; grouped `{ns}.{name}@{groupKey}` → `{ns}/{name}/{period}`.

### 3. Apply

```bash
java -jar cosid-migration.jar apply --plan plan.json --confirm-stopped --layout-config application.yaml
```

1. **Quiet period**: it snapshots every source key and machine record, waits `--observe` (2 minutes), reads them again,
   and **aborts if anything moved**: a 4.x instance is still running.
2. **Seal the sources** (on by default; `--no-seal-source` turns it off): each 4.x segment gets one atomic increment
   of `2^40` (`--seal-gap`; keep it the same across runs), and `lastMaxId` is read from the sealed value. A 4.x instance
   that was missed gets ranges far above 5.0's, and `verify` reports it.
3. **Write the target**: it raises each 5.0 key to `next`, records `{limit, tolerance}`, and prepares every slot with
   `next_floor = W`. The target only ever rises (`max(current, planned)`).

`apply` is idempotent. If it stops half way (a crash, a timeout), run it again. It refuses when a 5.0 runtime already
holds a slot in the namespace, or when the namespace has other slot parameters. The result, `apply-result.json`, holds
`T_apply`, W, and `notBefore` (W as ISO-8601).

### 4. Verify

```bash
java -jar cosid-migration.jar verify --plan plan.json --result apply-result.json
```

It reads both stores again and checks M1–M3, and that no 4.x segment or machine record moved after the seal. Exit code
`1` means a problem. The check stays valid after 5.0 has started.

### 5. Convert the configuration

```bash
java -jar cosid-migration.jar config --input application.yaml --result apply-result.json \
  --output application-5.yaml --zone Asia/Shanghai
```

The 4.x `cosid.*` configuration becomes the 5.0 model. **Everything it could not convert as is sits as comments at the
top of the output: review them.** A static slot gets `not-before: W` from the result. A 4.x proxy configuration becomes
a `remote` store (`baseUrl`, `token`, `connectTimeout`, `requestTimeout`).

### 6. Start 5.0

Deploy with the new configuration and the 5.0 artifacts (below). The first time-based id waits until the clock passes
W, at most about `sourceSkew`.

### Later: purge old periods

At run time periodic keys are sealed, never deleted (D23). Removing past periods physically is an offline step:

```bash
java -jar cosid-migration.jar purge --to jdbc:… --namespace shop --before 20250101 --dry-run
java -jar cosid-migration.jar purge --to jdbc:… --namespace shop --before 20250101 --confirm-stopped
```

Exit codes of every command: `0` done, `1` a check failed or the command aborted, `2` a usage error.

## Rollback

- **Segments**: safe. The seal only moved the 4.x values up, so 4.x continues above everything 5.0 could have used.
- **Time-based ids**: wait until the last 5.0 ceiling has passed (the lease `ttl` plus `tolerance`, 31s by default)
  before starting 4.x again.

## Artifacts and code

| 4.x | 5.0 |
|---|---|
| `cosid-spring-boot-starter` with Gradle feature variants | `cosid-spring-boot-starter` + `cosid-store-<backend>` |
| `cosid-spring-redis`, `cosid-jdbc`, `cosid-mongo`, `cosid-zookeeper` | `cosid-store-redis`, `-jdbc`, `-mongo`, `-zookeeper` |
| the proxy client / the proxy server | `cosid-store-remote` / the cosid-server image (new API only) |
| `cosid-jackson`, `cosid-mybatis`, `cosid-spring-data-jdbc`, `cosid-activiti`, `cosid-flowable`, `cosid-axon` | the same artifacts (D29), each depending on `cosid-core` and its library |
| `cosid-accessor` | `cosid-core` (`me.ahoo.cosid.accessor`) |
| `cosid-test` | `cosid-store-tck` |
| `cosid-dependencies` | `cosid-bom` |
| `cosid-sharding` | `cosid-sharding` (`ShardingRange` instead of Guava's `Range`) |
| Java 17 | **Java 21**, Spring Boot 4.0+ |

| 4.x API | 5.0 |
|---|---|
| `SnowflakeId`, `MillisecondSnowflakeId`, `SecondSnowflakeId`, `ClockSync*`, `StringSnowflakeId` | `IdGenerator` beans; `TimeBasedGenerator`, `Layout`, `ClockRegressionPolicy`; text through codecs |
| `SegmentId`, `SegmentChainId`, `DefaultSegmentId` | `SegmentGenerator` with a `BatchPolicy` |
| grouped segments, `generateGrouped` | `PeriodicSegmentGenerator`, `GroupedId` |
| `CosIdGenerator` (Radix62 / Friendly) | `cosid80` + `PartsCodec` |
| `IdConverter`s | `IdCodec`: `Radix`, `Friendly`, `DatePrefix`, prefix / suffix |
| `SnowflakeIdStateParser` | `Layout.unpack`, `Friendly.decode` |
| `IdGeneratorProvider`, `DefaultIdGeneratorProvider.INSTANCE`, the share | `CosIdRuntime`, the generator `default` |
| `MachineIdDistributor`s, the guarder, `~/.cosid-machine-state` | `cosid.machine.leased` / `static` (see [the slot lease](../concepts/slot-lease.md)) |
| `IntegerIdGenerator`, `UncertaintyIdGenerator`, `LazyIdGenerator` | `IdGenerators.toInt`, `.uncertain`, `.lazy` |
| `CosIdStoreProvider` | `StoreProvider` |
| `Customize*Properties` | `RuntimeSpecCustomizer` |
| Actuator DELETE of a generator | `cosid.generators.<name>.disabled: true` |
| `guarder.fail-fast: false` | not offered: it trades uniqueness for availability (P1) |
