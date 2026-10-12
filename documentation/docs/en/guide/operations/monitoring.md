# Monitoring

## Health

With Spring Boot Actuator, the `machineId` health indicator reports `DOWN` while a machine id lease could not be renewed, with the error and the time of the failed renewal. Alert on it: about 4 minutes 50 seconds after the last successful renewal (`safe-guard-duration - min(10s, safe-guard-duration / 10)`), Snowflake generators and `CosIdGenerator` stop with `MachineIdLostException`.

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
```

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
| `Guard Failed` (ERROR) | A lease renewal failed; the health indicator is `DOWN` until the next success |
| `Detected clock backwards` (WARN) | A generator or a new machine id owner is waiting for the clock |
| `Prefetch [...] - Hunger, Safety distance expansion` (INFO) | A segment chain ran dry and grows its prefetch distance; frequent messages mean `step` or `safe-distance` is too small for the load |
| `AppendChain [...] - prefetch failed` (ERROR), then `prefetch still failing` (WARN) | A segment chain cannot take segments from the store; generation continues from the reserved segments until they run out |
| `Revert machine id of [...] failed` (ERROR) | The machine id was not released on shutdown; it becomes free after the safe guard duration |

Set `logging.level.me.ahoo.cosid=debug` to follow distribution, renewal and prefetch in detail.
