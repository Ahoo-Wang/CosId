# Migrating from 4.x to 5.0

5.0 keeps no compatibility code (D1): not the API, not the configuration, not the store formats. The one bridge is
the offline tool **`cosid-migration`**. It reads the 4.x data, writes the 5.0 data so that new ids can never collide
with ids 4.x issued, and converts the configuration.

::: danger No mixed fleets
4.x and 5.0 **never run in the same namespace**, not even for a moment. Every 4.x instance of the namespace stops
before the migration, and 5.0 starts after it. This is not a rolling upgrade.
:::

A simple deployment may also migrate by hand during a downtime, without the tool: see
[Stop-the-world migration without the tool](#stop-the-world-migration-without-the-tool).

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

## Stop-the-world migration without the tool

**`cosid-migration` stays the recommended path**: it watches that 4.x really stopped, seals the source data, writes a
floor for time-based generators, and checks the result with `verify`. A simple enough deployment may instead migrate
by hand during a downtime. A manual migration has none of these safeguards: meeting M1–M3 above is entirely up to you.

### When a manual migration is possible

Only when **every** generator of the namespace is one of:

- **a time-based generator with an unchanged layout**: Snowflake or CosIdGenerator whose epoch, time unit and field
  widths are those of 4.x, that is the 4.x defaults (the 5.0 presets keep them).
- **a plain (non-periodic) segment**, share included (the `default` generator in 5.0).

**Use the tool** for:

- periodic (grouped) segments. A manual migration moves past the 4.x value with `initial-offset`, which is where
  **every new key** starts; a periodic generator creates a new key for every period, so every later period would start
  at that large offset as well: an order number that restarts at 1 every day would no longer start at 1.
- custom layouts or epochs, including a custom epoch on a second-based Snowflake.
- many keys, too many to check one by one; or when you want the safeguards: quiescence watch, automatic seal, `verify`.

As with the tool, 4.x and 5.0 never run in the same namespace: every 4.x instance stops first, 5.0 starts after.

### Time-based generators

1. **Stop every 4.x instance**, and note when the last one stopped (the stop time). A forgotten 4.x instance that holds
   the same machine id as a 5.0 instance issues the same ids at the same moment, and a manual migration will not
   catch it for you.
2. **Stay down** for at least the largest clock skew between any two hosts, and **at least 1 minute**. 4.x also let a
   host run up to 10s ahead of the store: count that in the skew.
3. **Check NTP sync on every host** (`chronyc tracking`, `timedatectl`, …): the skew must be within the downtime.
4. With StaticSlot you can also set a floor: a host whose clock is behind waits until it passes it.

   ```yaml
   cosid:
     machine:
       static:
         not-before: 2026-11-01T02:00:10Z   # stop time + largest clock skew
   ```

5. **Start 5.0**.

Why it works: with the same layout the time is in the highest bits, so a later id is always a larger number; the
downtime makes 5.0's first timestamp later than every timestamp 4.x issued.

::: warning A host whose clock is behind is not stopped
The tool writes a floor `W` into every slot of the new namespace. After a manual migration the 5.0 slots have a floor
of 0: a host whose clock is behind by more than the downtime issues ids in the time range 4.x used, and repeats ids if
it holds the same machine id. Leased slots have no floor you can set by hand: only the downtime and the NTP check
protect them.
:::

### Plain segments

1. **Stop every 4.x instance**.
2. **Read each segment's 4.x value, `last_max_id`**. `{ns}` is the 4.x `cosid.namespace`, `{name}` the segment name;
   the share segment is named `__share__`:

   | 4.x backend | Location | Read |
   |---|---|---|
   | JDBC | table `cosid`, `name = '{ns}.{name}'` | `select last_max_id from cosid where name = 'shop.order';` |
   | Redis | string `cosid:{ns.name}.adder` (the braces are part of the key) | `GET "cosid:{shop.order}.adder"` |
   | MongoDB | collection `cosid`, `_id = '{ns}.{name}'` | `db.cosid.findOne({_id: "shop.order"}).lastMaxId` |
   | ZooKeeper | node `/cosid/{ns}.{name}`, an 8-byte big-endian integer | `zkCli` cannot show it: read it with `plan` below |

   The read-only `cosid-migration plan` (step 2 of the runbook) also reads them all at once: its report lists the 4.x
   value of every segment.
3. **(Recommended) Seal by hand**: atomically add a large number to the 4.x value (for example `2^40` =
   `1099511627776`), then take `last_max_id = sealed value − 2^40`. Every fetch before the seal is counted, and a
   forgotten 4.x instance gets ranges far away from 5.0's. On a rollback 4.x continues from the larger value, still
   without repeats; without the seal a rollback needs one more step (see [Rollback](#rollback)).

   | 4.x backend | Seal (returns or reads back the sealed value) |
   |---|---|
   | JDBC | in one transaction: `update cosid set last_max_id = last_max_id + 1099511627776 where name = 'shop.order';` then `select last_max_id from cosid where name = 'shop.order';` |
   | Redis | `INCRBY "cosid:{shop.order}.adder" 1099511627776` |
   | MongoDB | `db.cosid.findOneAndUpdate({_id: "shop.order"}, {$inc: {lastMaxId: NumberLong("1099511627776")}}, {returnDocument: "after"})` |
   | ZooKeeper | no shell command does it; use the tool if you want a seal |

4. **Set `initial-offset = last_max_id + 1` on the matching 5.0 generator** (see the
   [generator configuration](../../reference/config/generators.md)). The share segment is the `default` generator.

   ```yaml
   cosid:
     namespace: shop
     generators:
       order:
         strategy: segment
         store: main
         initial-offset: 120001   # the 4.x last_max_id was 120000
   ```

5. **Start 5.0**.

Why it works: every 4.x fetch atomically moves `last_max_id` forward by a step, so it bounds every range any instance
reserved; 5.0's first id is `last_max_id + 1`.

::: warning `initial-offset` applies to new keys only
It is used only when 5.0 creates the key. Set it **before 5.0 first starts**; once 5.0 has issued ids for the key (a
trial run, say), it no longer has any effect: make sure the key does not exist in the 5.0 store yet.
:::

## Rollback

Stop every 5.0 instance of the namespace first: 4.x and 5.0 do not run side by side on the way back either.

- **Segments, sealed** (`apply` without `--no-seal-source`, or the manual seal): 4.x can start again as it is. The seal
  moved each 4.x value up by the seal gap (2^40), above every id 5.0 issued from the key, as long as 5.0 reserved
  fewer than 2^40 ids on it.
- **Segments, not sealed** (`apply --no-seal-source`, or a manual migration without step 3): **do not start 4.x as it
  is**. 4.x continues from its `last_max_id`, and 5.0 issued from `last_max_id + 1` on: 4.x would issue those ids
  again. Before starting 4.x, move each 4.x value above everything 5.0 reserved on the key, for example by adding the
  seal gap now, with the commands of [the manual seal](#plain-segments):

  | 4.x backend | Move the 4.x value up |
  |---|---|
  | JDBC | `update cosid set last_max_id = last_max_id + 1099511627776 where name = 'shop.order';` |
  | Redis | `INCRBY "cosid:{shop.order}.adder" 1099511627776` |
  | MongoDB | `db.cosid.updateOne({_id: "shop.order"}, {$inc: {lastMaxId: NumberLong("1099511627776")}})` |
  | ZooKeeper | no shell command does it: write the 8-byte big-endian value of the node `/cosid/shop.order` with a ZooKeeper client (Curator `setData`), while every 4.x and 5.0 instance is stopped |

  Adding 2^40 is enough while 5.0 reserved fewer than 2^40 ids on the key; otherwise set the 4.x value to at least the
  largest id 5.0 reserved. Do it for every migrated key, the share (`{ns}.__share__`) and the periodic keys included.
- **Time-based ids**: wait until the last 5.0 ceiling has passed (the lease `ttl` plus `tolerance`, 31s by default)
  before starting 4.x again.

## Known limitations

- **A CosIdGenerator in a namespace of its own cannot migrate yet.** A 4.x deployment that sets
  `cosid.generator.namespace` to a namespace other than `cosid.namespace` shares the CosIdGenerator machine ids of that
  namespace across services, to keep their 80-bit ids unique across them. This 5.0 release has no counterpart yet: a
  converted `cosid80` would take its slot in the service namespace and issue the ids of other services again. `config`,
  and `plan` / `apply` with `--layout-config`, refuse such a configuration. Keep those services on 4.x until named
  machines (D37) come in a later 5.0 release, or issue the 80-bit ids of every service from one cosid-server. A
  `cosid.generator.namespace` equal to `cosid.namespace`, or not set, migrates as usual.

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
