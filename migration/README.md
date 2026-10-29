# cosid-migration

The offline 4.x → 5.0 migration tool ([09-migration.md](../docs/architecture/09-migration.md)), the only bridge
between the two versions (D1). An executable fat jar, not published as a library:

```bash
./gradlew :migration:shadowJar          # migration/build/libs/cosid-migration.jar, Java 21
java -jar cosid-migration.jar --help
```

It reads the 4.x formats itself (read-only, except for sealing) and writes the 5.0 schemas of `store/*` through a narrow
admin path of its own: it only ever raises `next` and `next_floor` (`max(current, planned)`), so every command can run
again. Source and target can be different store types (Redis → JDBC and so on).

The one hard constraint: **ids issued after the migration never collide with ids 4.x issued** (09 §1):

| # | Constraint | How |
|---|---|---|
| M1 | segments: 5.0's first range is past everything 4.x reserved | `next = lastMaxId + 1 + gap` per key |
| M2 | time-based ids: 5.0's first timestamp is after every 4.x timestamp | every slot of the namespace gets `next_floor = W` |
| M3 | 5.0 and 4.x time-based ids do not meet in one column | 4.x layouts and epochs are kept (D16); `plan` validates others |

`W = max(T_apply, T4 + 1s) + sourceSkew`: `T_apply` is the target store's clock when `apply` writes, `T4` the largest
timestamp of the 4.x machine records, `sourceSkew` (10s) the clock skew 4.x allowed.

## Operator runbook (运维手册)

1. **Stop every 4.x instance of the namespace**, including segment-only services and Manual / StatefulSet instances,
   which leave no machine record behind. 4.x and 5.0 never share a namespace.
2. **Plan** (read-only on both stores) with the 4.x configuration, so that layouts (M3), group patterns and the share are
   known:
   ```bash
   java -jar cosid-migration.jar plan --from redis://redis-4x:6379 --to jdbc:mysql://db/cosid --to-option user=cosid \
     --namespace shop --layout-config application.yaml
   ```
   Read the report: every key with its new key and value, `NOT MIGRATED` keys and why, the W estimate, the layout checks
   and the warnings. Fix what it asks for (`--rename`, `--group-pattern`, `--slot-bits`, `--tolerance`) and plan again.
   Passwords: `--from-option password=...` / `--to-option password=...` or `COSID_MIGRATION_FROM_PASSWORD` /
   `COSID_MIGRATION_TO_PASSWORD`, on every command; they are never written to `plan.json`.
3. **Apply**:
   ```bash
   java -jar cosid-migration.jar apply --plan plan.json --confirm-stopped --layout-config application.yaml
   ```
   It watches the 4.x store for `--observe` (2 minutes) and aborts if anything moved: a 4.x instance still runs. It then
   seals each 4.x segment (`+ 2^40`), raises the 5.0 keys, and prepares the slots with `next_floor = W`. If it stops half
   way (a crash, a timeout), run it again: a segment already at `planned + seal gap` counts as sealed, and the target only
   ever rises.
4. **Verify**: `java -jar cosid-migration.jar verify --plan plan.json --result apply-result.json` (exit code 1 on a
   problem). It reads both stores again and checks M1–M3, and that no 4.x segment or machine record moved after the
   migration; it stays valid after 5.0 started.
5. **Convert the configuration**:
   ```bash
   java -jar cosid-migration.jar config --input application.yaml --result apply-result.json --output application-5.yaml --zone Asia/Shanghai
   ```
   Everything it could not convert as is sits as comments at the top of the output: review them. A static slot gets
   `not-before: W`.
6. **Start 5.0.** The first time-based id waits until the clock passes W, at most about `sourceSkew`.
7. Later, as an offline maintenance step **with every 5.0 instance stopped** (a store must never see a key's node
   deleted while it uses the key), physically remove sealed period keys of past periods (D23):
   `java -jar cosid-migration.jar purge --to jdbc:... --namespace shop --before 20250101 --dry-run`, then with
   `--confirm-stopped` instead of `--dry-run`.

**Rollback** to 4.x, after stopping 5.0: sealed segments are safe, the seal moved the 4.x values `2^40` up. **Without
the seal (`--no-seal-source`) 4.x would issue again what 5.0 issued from `lastMaxId + 1`**: before starting 4.x, move
each migrated 4.x value above everything 5.0 reserved on the key (add the seal gap by hand; the commands per backend
are in the migration guide, "Rollback"). For time-based ids, wait until the last 5.0 ceiling has passed (the lease
`ttl` plus `tolerance`, 31s by default) before starting 4.x again.

**A forgotten 4.x instance** after `apply --observe 0`: the seal keeps its new ranges `2^40` above 5.0's, and `verify`
reports the segment as grown. It cannot keep its time-based ids apart, which is what the quiet period is for.

## Stores

| Location | 4.x data read | 5.0 data written |
|---|---|---|
| `jdbc:<url>`, options `user`, `password` | `cosid(name = '{ns}.{name}', last_max_id)`, `cosid_machine` (`segmentTable` / `machineTable` options rename them) | `cosid_range`, `cosid_slot`, `cosid_namespace` (`rangeTable` / `slotTable` / `namespaceTable`; `initSchema=true` creates them; `dialect` overrides the detection) |
| `redis://` / `rediss://`, option `cluster=true` | `cosid:{ns.name}.adder`; `cosid:{ns}:itc_idx`, `:revert`, `:adder`, `:stable`, `:lease` | `cosid:{key}` and `cosid:{key}:sealed`; `cosid:{ns}:meta`, `:slot`, `:free` (rows encoded by store/redis `slot-row.lua`) |
| `mongodb://`, option `database` (`cosid_db`) | `cosid {_id: '{ns}.{name}', lastMaxId}`, `cosid_machine` | `cosid_range`, `cosid_namespace`, `cosid_slot` (+ its indexes) |
| `zookeeper://host:port[/chroot]` | `/cosid/{ns}.{name}` (8-byte counter), `/cosid/{ns}/__itc_idx`, `__revert`, `__counter`, `__stable` | `/cosid/range/{key}`, `/cosid/slot/{ns}` and its slot nodes |

Redis writes (the 4.x seal, the 5.0 raise, the slot preparation) wait for a replica: when the written node has a
connected replica, each write is followed by `WAIT 1`, and the step stops with an error if no replica acknowledges
within 5 s, since a failover would lose the write. Check the replication and run the step again. A node without
replicas is not waited for.

Key mapping (09 §4.1): `{ns}.{name}` → `{ns}/{name}`; grouped `{ns}.{name}@{groupKey}` → `{ns}/{name}/{period}`, every
period, past ones included. A group key must be a 5.0 preset (`yyyy`, `yyyyMM`, `yyyyMMdd`), or its 4.x pattern is named
(`group.pattern` of `--layout-config`, or `--group-pattern name=yyyy-MM-dd`) and the key is reformatted to the preset of
the same granularity. The 4.x share `__share__` becomes `default` (`segment-share` when the 4.x snowflake share is also
enabled; `--rename` overrides). 5.0 generator names cannot contain `/`. Keys that do not map are listed, never migrated.

## `plan.json`

| Field | Meaning |
|---|---|
| `format` | `cosid-migration-plan/1` |
| `createdAt` | ISO-8601 |
| `namespace` | the namespace, the same in 4.x and 5.0 |
| `source`, `target` | `{type, url, options}` without passwords |
| `slots` | `{bits, limit, toleranceMillis}` the 5.0 namespace gets: `--slot-bits` (the 4.x `machine-bit`, 10 by default), `--tolerance` (1s) |
| `renames` | 4.x name → 5.0 generator name |
| `groupPatterns` | 5.0 generator → 4.x group key pattern |
| `segments[]` | `{sourceName, generator, period, targetKey, lastMaxId, next, targetNext}`: `next = lastMaxId + 1` before `--gap`; `targetNext` what the target already has |
| `unrecognized[]` | keys not migrated, with the reason |
| `machines` | `{records, maxTimestamp (T4), targetNow, sourceSkewMillis, estimatedW}` |
| `layouts[]` | `{generator, source, target, ok, reason}`, M3 per time-based generator |
| `warnings[]` | everything else to read |

## `apply-result.json`

| Field | Meaning |
|---|---|
| `format` | `cosid-migration-result/1` |
| `planSha256` | SHA-256 of the plan file applied; `verify` checks it |
| `namespace`, `source`, `target` | as in the plan |
| `appliedAt` | `T_apply`, the target store clock, epoch milliseconds |
| `sourceMaxTimestamp` | T4 |
| `sourceSkewMillis`, `gap`, `sealed`, `sealGap`, `observedMillis` | the apply options as they ran |
| `w`, `notBefore` | W in epoch milliseconds and ISO-8601 (`cosid.machine.static.not-before`) |
| `slots` | `{bits, limit, toleranceMillis}` |
| `segments[]` | `{sourceName, targetKey, lastMaxId, next, targetNext, sealedAt}`: `lastMaxId` bounds what 4.x reserved before the seal, `next = lastMaxId + 1 + gap`, `targetNext` the target after the write, `sealedAt` the 4.x value after the seal |
| `warnings[]` | everything else to read |

## Commands

```bash
plan    --from <4.x> --to <5.0> --namespace <ns> [--output plan.json] [--layout-config <4.x yaml|properties>]
        [--machine-from <4.x machine store>] [--machine-to <5.0 slot store>]
        [--target-config <5.0 yaml>] [--slot-bits 10] [--tolerance 1s] [--source-skew 10s]
        [--rename old=new]... [--group-pattern generator=pattern]... [--from-option k=v]... [--to-option k=v]...
apply   --plan plan.json --confirm-stopped [--observe 2m] [--gap 0] [--source-skew 10s] [--no-seal-source]
        [--seal-gap 1099511627776] [--result apply-result.json] [--layout-config ..] [--target-config ..]
        [--allow-unmigrated <4.x key>]... [options]
verify  --plan plan.json --result apply-result.json [options]
config  --input application.yaml --result apply-result.json --output application-5.yaml [--zone <zone>] [--allow-new <generator>]...
purge   --to <5.0> --namespace <ns> --before <period> (--confirm-stopped | --dry-run) [--to-option k=v]...
```

Exit codes: 0 done, 1 a check failed or the command aborted, 2 a usage error.

What keeps a plausible configuration from issuing 4.x ids again (pre-RC review):

- **Namespaces of their own.** A 4.x definition with `namespace: other` keeps its keys as `other.{name}` (and a snowflake
  its machine id in `other`). With `--layout-config`, `plan` reads those namespaces too and maps `other.{name}` to
  `{ns}/{name}`, the generator 5.0 gives it; a key `shop.v2.order` of namespace `shop.v2` is not taken for a segment
  `v2.order` of `shop`. Without `--layout-config` a dotted segment name is reported.
- **A CosIdGenerator in a namespace of its own** (`cosid.generator.namespace` other than `cosid.namespace`, D37): 4.x
  shares its machine ids across services to keep their 80-bit ids apart; 5.0 keeps them in the named machine
  `cosid.machines.generator`. Migrate the shared namespace **once**, before any service: `plan --namespace <shared>
  --layout-config <a service's yaml>` reads the largest 4.x machine id M there. The default slot offset puts the 5.0 slots at
  the top of the nodes, `2^node-bits - 2^bits` (`2^20 - 1024` with the 4.x defaults), which leaves 4.x the most room
  below them; `--slot-offset` overrides it, above M with `offset + 2^bits <= 2^node-bits`. `plan` reports and records the
  `headroom`, `offset - (M + 1)`: how many more machine ids 4.x can hand out before its nodes meet 5.0's, loudly when it
  is below `2^bits`. 4.x could hand out ids up to its hard cap `2^(cosid.generator.machine-bit)`, which `config` keeps as
  `node-bits`, so the separation is not by construction (`guaranteed: false`): 4.x must stay below the offset. With an offset, `apply` needs no `--confirm-stopped` and
  watches no quiet period: 4.x keeps running while each service moves, and `verify` fails once 4.x hands out a machine
  id at or above the offset. `--slot-offset 0` migrates it at once,
  every sharing service stopped. Each service's `apply` then refuses unless the shared namespace is prepared as its plan
  saw it, and `config` writes `cosid.machines.generator` (with that `slot-offset`) and `machine: generator` on cosid80.
  With the CosIdGenerator disabled, the machine records of that namespace count for T4 and the quiet period.
- **CosIdGenerator bits.** `config` keeps the 4.x `timestamp-bit`, `machine-bit`, `sequence-bit` and
  `sequence-reset-threshold` (a threshold beyond the sequence range is taken as its largest value) and the friendly
  `pad-start`; bits whose time and sequence exceed 63 are refused by `plan` and `config`.
- **The share.** `__share__` is `default`, or `segment-share` when the 4.x snowflake share is enabled too: `plan`
  refuses it without `--layout-config` or `--rename __share__=<generator>`.
- **Keys that do not map** (a group pattern 5.0 cannot read, a name with `/`): `apply` aborts unless each is named with
  `--allow-unmigrated <ns>.<name>`; `verify` reports any 4.x key the result does not have, such as one created after
  `apply` by an instance left running.
- **Separate 4.x stores.** With `--layout-config`, `plan` compares `cosid.segment.distributor.type` with `--from` and
  `cosid.machine.distributor.type` with `--from` or `--machine-from`; when 4.x kept them apart it also wants
  `--machine-to`, the 5.0 store of `cosid.machine.leased` that gets the slots.
- **`config` continues the migrated keys.** It names the generators as the plan did (`--rename`, the share) and fails
  unless each segment or periodic segment generator has its key in `apply-result.json`; a generator 4.x never issued
  from is named with `--allow-new`. The slot `bits` and `tolerance` come from the result.
- **Credentials.** `plan.json` keeps no password: the password of a URL's userinfo and `password=` / `pwd=` parameters
  are removed, so give them again with `--from-option password=...` (MongoDB takes `user` / `password` /
  `authSource` options too). The account option is `user`, or `username` as the 5.0 JDBC store names it.
- W is taken from the later of the source and target store clocks.

`apply` refuses when the 5.0 namespace has a live slot lease (5.0 runs) or other slot parameters, and, given
`--layout-config`, checks M3 again with the real W.

## Tests

The tests write real 4.x data with the released 4.1.0 jars: `src/test/legacy4x` is a source set compiled against them
alone and loaded in a class loader of its own (4.x and 5.0 share the package `me.ahoo.cosid`). Each scenario
(`MigrationScenarioTest`) migrates segments, grouped segments of the current and past periods and machine ids, issues
ids with a 5.0 `CosIdRuntime` and asserts M1–M3; leaves a 4.x instance running (the quiet period aborts `apply`; with
the check off, the seal keeps its ranges away); and interrupts `apply` before and after the seal. H2 (over TCP) and
ZooKeeper (curator-test) always run, Redis and MongoDB in Testcontainers when Docker is available; the cross-type cases
are ZooKeeper → H2, Redis → H2 and MongoDB → ZooKeeper. `config` has goldens under `src/test/resources/config`
(`-Pcosid.migration.updateGoldens=true` rewrites them; each case has the `apply-result.json` of its keys), and `ConfigBindingTest` binds each golden through Spring Boot's
YAML loader and `Binder` into the `CosIdProperties` of `spring/autoconfigure` and maps it with `CosIdSpecs.toSpec`. That
edge is test scope only (`DependencyRules.TEST_EDGES`); `checkNoSpring` fails the build if the fat jar carries Spring.
