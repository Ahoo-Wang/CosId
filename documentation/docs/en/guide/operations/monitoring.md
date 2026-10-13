# Monitoring

## Health

With Spring Boot Actuator, the `machineId` health indicator reports `DOWN`:

- while a machine id lease could not be renewed, with the error and the time of the failed renewal (`error`, `guardAt`). It turns `UP` again at the next successful renewal;
- when a lease has expired or was lost, that is, taken over by another instance. This stays `DOWN` even if a later renewal succeeds, because the generators of that machine id have stopped: restart the instance.

Alert on it: about 4 minutes 50 seconds after the last successful renewal (`safe-guard-duration - min(10s, safe-guard-duration / 10)`), Snowflake generators and `CosIdGenerator` stop with `MachineIdLostException`.

The `leases` detail shows each lease the instance holds, by namespace and instance id:

| Field | Meaning |
|-------|---------|
| `machineId` | The machine id of the lease |
| `instanceId` | The instance id that holds it |
| `remainingMillis` | Time left before the lease expires; the generators stop when it reaches zero |
| `lost` | `true` once another instance took the machine id over |
| `expired` | `true` once the lease ran out without a renewal |
| `released` | `true` once the lease was given back, on shutdown |

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
```

## Metrics

With Micrometer on the classpath and a `MeterRegistry` bean (Spring Boot Actuator brings both), the starter registers `CosIdMeterBinder`. Turn it off with `cosid.metrics.enabled=false`.

| Meter | Type | Tags | Meaning |
|-------|------|------|---------|
| `cosid.segment.remaining` | gauge | `name`, `segment` | IDs left in the segments a segment generator already holds; alert when it stays near zero |
| `cosid.segment.prefetch.distance` | gauge | `name`, `segment` | Segments a segment chain prefetches ahead; it grows under load up to `max-prefetch-distance` |
| `cosid.segment.fetch` | timer | `segment`, `outcome` | Segment fetches from the store, `success` or `failure` |
| `cosid.segment.prefetch.expansions` | counter | `segment` | Times a chain ran dry and grew its prefetch distance |
| `cosid.segment.prefetch.failures` | counter | `segment` | Failed prefetches |
| `cosid.machine.lease.remaining` | time gauge | `namespace` | Time left on the machine id lease; Snowflake generators stop at zero. `+Inf` for a lease that never expires (guarder off) |
| `cosid.machine.clock.skew` | time gauge | `namespace` | How far this host's clock was ahead of the store's at the last renewal |
| `cosid.machine.lease.acquired` | counter | `namespace`, `claim` | Leases taken: `self`, `released`, `new` or `expired` |
| `cosid.machine.lease.renew.failures` | counter | `namespace` | Failed lease renewals |
| `cosid.machine.lease.lost` | counter | `namespace` | Leases lost to another instance |
| `cosid.clock.backwards` | counter | `type` | Times a generator of that class saw the clock move backwards |
| `cosid.clock.sync.wait` | timer | `type` | Waits for the clock to catch up |
| `cosid.sequence.exhausted.wait` | timer | | Waits for the next tick after a generator used up its sequence |
| `cosid.call.timeouts`, `cosid.call.rejections` | counter | `operation` | Store calls that timed out, or were rejected beyond `cosid.max-concurrent-calls` |
| `cosid.generator.generated` | function counter | `name` | IDs generated, only with `cosid.metrics.generated.enabled=true` |

`cosid.metrics.generated.enabled` is off by default: it wraps segment and Snowflake generators in a counting decorator (`CountedSegmentId`, `CountedSnowflakeId`), one atomic add per ID. Beans keep their `SegmentId` and `SnowflakeId` types. The beans are decorators already (for example `StringSegmentId` around a `SegmentChainId`), so to reach the generator use the recursive `Decorator.getActual(bean)`, not one `getActual()` followed by `instanceof`: with the flag on there is one more layer.

With `cosid.provider.isolated=true`, each application context counts only the lease events of its own instances (so each isolated context needs its own instance id: the default non-stable `{host}:{pid}:{random}` is, a fixed `cosid.machine.instance-id` or the stable default `{host}:{pid}` is not), and the segment events of the segment stores its generators use: two contexts with generators on the same store both count that store's events. Clock, sequence and call meters cannot tell contexts apart and count the whole JVM. Generators added to the provider after startup get their gauges at the next `CosIdMeterBinder.refresh()`.

### Alerts

| Alert | Severity | Example PromQL |
|-------|----------|----------------|
| Lease close to expiring: below about a third of `safe-guard-duration` (5m by default) | warning | `cosid_machine_lease_remaining_seconds < 100` |
| Lease renewals failing | warning | `increase(cosid_machine_lease_renew_failures_total[5m]) > 0` |
| Lease lost: the instance's Snowflake generators stop | critical | `increase(cosid_machine_lease_lost_total[5m]) > 0` |
| Segment prefetch failing: generation runs on the reserved segments | warning | `increase(cosid_segment_prefetch_failures_total[5m]) > 0` |
| Segment chain running dry | warning | `cosid_segment_remaining < 1000 and on(name, segment) cosid_segment_prefetch_distance` (segment chains only: a `DefaultSegmentId` holds one segment and runs low before every fetch; pick a value from your `step`) |
| Store calls rejected | warning | `increase(cosid_call_rejections_total[5m]) > 0` |

The health indicator turns `DOWN` on a lost or expired lease too; alert on whichever your platform already scrapes.

## Generator state

The `cosid` endpoint lists every generator with its kind, converter and state: the machine id and last timestamp of Snowflake generators, the current segment of segment generators.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, cosid
```

`GET /actuator/cosid` returns all generators and `GET /actuator/cosid/{name}` one of them. The `cosidGenerator` and `cosidStringGenerator` endpoints generate IDs: expose them only to trusted callers. The `DELETE` operation of `cosid` removes a generator and is off unless `cosid.actuator.delete.enabled=true`. Protect the management port like any other administrative interface.

## Logs

| Logger message | Meaning |
|----------------|---------|
| `Guard Failed` (ERROR) | A lease renewal failed; the health indicator is `DOWN` until the next success, and stays `DOWN` if the lease was lost or expired meanwhile |
| `Detected clock backwards` (WARN) | A generator or a new machine id owner is waiting for the clock |
| `Prefetch [...] - Hunger, Safety distance expansion` (INFO) | A segment chain ran dry and grows its prefetch distance; frequent messages mean `step` or `safe-distance` is too small for the load |
| `AppendChain [...] - prefetch failed` (ERROR), then `prefetch still failing` (WARN) | A segment chain cannot take segments from the store; generation continues from the reserved segments until they run out |
| `Revert machine id of [...] failed` (ERROR) | The machine id was not released on shutdown; it becomes free after the safe guard duration |

Set `logging.level.me.ahoo.cosid=debug` to follow distribution, renewal and prefetch in detail.
