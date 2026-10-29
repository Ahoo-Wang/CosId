# Metrics and Health

With Spring Boot Actuator and Micrometer on the classpath, the starter registers a meter listener, a health indicator
and an endpoint. Without Spring, implement `GeneratorListener` (passed in `RuntimeOptions`) and read
`runtime.describe()` and `runtime.slot()`.

## Meters

Every meter is named `cosid.*` (19 in all). They come from listener events, so the hot path never calls them; only
`cosid.generator.generated` counts ids, and only when `cosid.metrics.generated.enabled: true`. To turn every `cosid.*`
meter off (4.x `cosid.metrics.enabled: false`), use Spring Boot's meter filter: `management.metrics.enable.cosid: false`.

| Meter | Type | Tags | 4.x name |
|---|---|---|---|
| `cosid.segment.reserve` | timer | `name` | `segment.fetch` |
| `cosid.segment.refill.failures` | counter | `name`, `error` | `segment.prefetch.failures` |
| `cosid.segment.batch.resizes` | counter | `name`, `direction` (`up`, `down`) | `segment.prefetch.expansions` |
| `cosid.segment.batch` | gauge | `name` | `segment.prefetch.distance` |
| `cosid.segment.held` | gauge | `name` | `segment.remaining` |
| `cosid.sequence.exhausted` | counter | `name` | `sequence.exhausted.wait` |
| `cosid.clock.backwards` | summary (ms) | `name`, `action` (`borrow`, `wait`, `fail`) | `clock.backwards`, `clock.sync.wait` |
| `cosid.machine.window.exceeded` | counter | `name` | |
| `cosid.machine.lease.acquired` | counter | `namespace`, `machine`, `reason` (`idempotent`, `preferred`, `sticky`, `lowest`) | `machine.lease.acquired` |
| `cosid.machine.lease.renewed` | counter | `namespace`, `machine` | |
| `cosid.machine.lease.released` | counter | `namespace`, `machine` | |
| `cosid.machine.lease.renew.failures` | counter | `namespace`, `machine` | `machine.lease.renew.failures` |
| `cosid.machine.lease.lost` | counter | `namespace`, `machine` | `machine.lease.lost` |
| `cosid.machine.lease.remaining` | time gauge | `namespace`, `machine` | `machine.lease.remaining` |
| `cosid.machine.window.remaining` | time gauge | `namespace`, `machine` | |
| `cosid.machine.clock.skew` | time gauge | `namespace`, `machine` | `machine.clock.skew` |
| `cosid.machine.owner.duplicates` | counter | `namespace`, `machine` | |
| `cosid.store.timeouts` | counter | `store`, `operation` (`reserve`, `seal`, `acquire`, `renew`, `release`: a store call hit its timeout; `reserve-wait`: callers waited for a refill past `reserve-timeout`, once per refill) | `call.timeouts` |
| `cosid.generator.generated` | function counter | `name` | `generator.generated` |

The `cosid.machine.*` meters of a lease carry the `machine` tag (`default` or the [named machine](../concepts/slot-lease.md#named-machines))
next to its `namespace`. The segment meters have no per-key tag, so a periodic segment adds no meters as its periods
roll. `cosid.generator.generated` counts every view the runtime hands out, `IdFiller` included. cosid-server adds
`cosid.server.call.rejections{reason}`.

## Health

The health indicator `cosid` (`/actuator/health/cosid`; switch `management.health.cosid.enabled`):

- `DOWN` when a machine's slot lease was lost and not acquired again, or when a time-based generator's slot binding
  is no longer valid; otherwise `UP`. Without a time-based generator there is no slot, no details, and it is `UP`.
- Details: `machines`, one entry per started machine (`default` and each [named machine](../concepts/slot-lease.md#named-machines)):
  `namespace`, `slot`, `valid`, `owner`, `fence`, `expiresAt` and `expiresInMillis`, `ceiling` and
  `windowRemainingMillis`, `lost`, `renewFailures` (in a row) and `lastRenewError`.

```json
{"status":"UP","details":{"machines":{"default":{"namespace":"order-service","slot":0,"valid":true,
 "owner":"app-7d9f:8080","fence":1,"expiresAt":1793098633174,"expiresInMillis":28124,
 "ceiling":1793098634174,"windowRemainingMillis":29124,"lost":false,"renewFailures":0}}}}
```

## The `cosid` endpoint

| Request | Answer |
|---|---|
| `GET /actuator/cosid` | `describe()`: every generator's strategy, store and details |
| `GET /actuator/cosid/{name}` | one generator |
| `GET /actuator/cosid/{name}/long` | a new id (`snowflake`, `segment`) |
| `GET /actuator/cosid/{name}/string` | a new text id (every strategy) |

Expose it with `management.endpoints.web.exposure.include: health,cosid`. It replaces 4.x's `cosid`,
`cosidGenerator` and `cosidStringGenerator` endpoints.

## Listening yourself

```java
public interface GeneratorListener {
    default void onRangeReserved(String generator, String key, Range range, Duration latency) {}
    default void onRefillFailed(String generator, String key, Throwable error) {}
    default void onBatchResized(String generator, long oldSize, long newSize) {}
    default void onSequenceExhausted(String generator) {}
    default void onClockRegression(String generator, long backwardsMillis, String action) {}
    default void onWindowExceeded(String generator) {}
    default void onSlotAcquired(SlotLease lease, String reason) {}
    default void onSlotRenewed(SlotLease lease) {}
    default void onSlotRenewFailed(SlotLease lease, Throwable error) {}
    default void onSlotLost(SlotLease lease) {}
    default void onSlotReleased(SlotLease lease) {}
    default void onClockSkew(String namespace, long skewMillis) {}
    default void onDuplicateOwnerKey(String namespace, String ownerKey) {}
    default void onStoreTimeout(String store, String operation) {}
}
```

In Spring, `GeneratorListener` beans receive the events next to the meter listener.
