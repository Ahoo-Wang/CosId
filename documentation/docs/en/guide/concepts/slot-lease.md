# Machine Slot Lease

A Snowflake id is `(time, slot, sequence)`. Two instances never collide if they never issue **the same timestamp on the
same slot**. 4.x tried to guarantee that by giving each instance a slot (its "machine id"). It then had to trust that
clocks were close, that every instance used the same safe-guard duration, and that a local watermark was written in
time. 5.0 drops those assumptions.

## The idea: lease a time window, not a slot

A lease in 5.0 is the exclusive right to issue **timestamps in a window** `(floor, ceiling]` on one slot:

```
the holder may issue timestamp t only if   floor < t ≤ ceiling
the next holder's floor                  ≥ the largest ceiling the previous holder could have used
```

Successive windows on a slot touch but never overlap. The store keeps that inequality by itself, in one atomic
compare-and-set per change, and the generator checks every id against its window before it issues it. **Uniqueness
follows from that alone (D22): it depends on no clock being right, on no GC pause being short, on no network being
fast.** Clocks only decide *when* a lease expires and someone else may take the slot. That is availability, not
safety.

```mermaid
gantt
    dateFormat  X
    axisFormat  %s
    section slot 3
    holder A (floor 0, ceiling 40)   :a, 0, 40
    holder B (floor 40, ceiling 95)  :b, 40, 95
    holder C (floor 95, ...)         :c, 95, 130
```

| What happens | Effect | Uniqueness |
|---|---|---|
| a host clock runs ahead of the store by more than `tolerance` | the generator reaches its ceiling early and waits for the renewal | kept |
| a host clock runs behind the store | a new holder waits longer to pass its floor | kept |
| the store's clock jumps (failover, NTP) | leases expire earlier or later | kept: floor and ceiling only grow |
| the process pauses past its expiry (GC, VM freeze) | after the pause its timestamps lie past the ceiling: it refuses to issue | kept |

## The lease

```java
public record SlotLease(String namespace, int slot, Owner owner,
                        long fence,      // +1 on every change of ownership; renewals keep it
                        long expiresAt,  // store clock, ms: when someone else may take the slot
                        long floor,      // ms, excluded: this holder's timestamps are > floor
                        long ceiling,    // ms, included: this holder's timestamps are ≤ ceiling
                        long storeNow) {}

public record Owner(String key, UUID incarnation) {}
```

- `ceiling = expiresAt + tolerance`. `tolerance` (default 1s, D20) is how far past the store's expiry a holder may still
  issue. It only trades availability: a larger tolerance rides out more clock skew, and a crashed holder's slot stays
  unusable for longer.
- The **owner key** is the logical identity (`host:port` or `host:pid` by default, or configured). The
  **incarnation** is a UUID per runtime start. A live lease of the same key but another incarnation is **never** taken
  over: the new process gets another slot and reports `onDuplicateOwnerKey`. 4.x took the slot back and could issue
  duplicates for up to a minute.

### Choosing a slot

Each try is one atomic conditional update of a single slot; the order is only a preference:

1. the slot this `(key, incarnation)` already holds: an idempotent retry;
2. `preferred-slot` if it is free (a StatefulSet ordinal, say);
3. a free slot whose last owner had the same key ("sticky", 4.x's *stable*, without the never-expiring lease);
4. the lowest free slot below `2^bits`;
5. none: `Exhausted`, with the earliest expiry to wait for.

### Store-side changes

| Operation | Condition | Change |
|---|---|---|
| acquire | the slot is free or expired | `fence + 1`; `floor = next_floor`; `expiresAt = now + ttl`; `ceiling = max(expiresAt + tolerance, floor)`; `next_floor = ceiling` |
| renew | incarnation and fence match | `expiresAt = max(expiresAt, now + ttl)`; `ceiling` and `next_floor` follow; fence unchanged, so a replayed renewal succeeds |
| release | incarnation and fence match | owner cleared; `next_floor = max(floor, min(lastIssued, ceiling))`; `fence + 1` |

A clean release hands the exact last issued timestamp to the next holder, which can start right after it.

## LeaseKeeper

Each `CosIdRuntime` has one `LeaseKeeper` (D18), and every time-based generator of the runtime shares its slot.

- It renews at `ttl/3`. A failed renewal is retried with back-off (from 100ms, up to `ttl/6`). Until the ceiling the
  generators keep issuing.
- When a renewal finds the lease gone, the old binding is invalidated (generators fail fast with `SlotLostException`)
  and a new slot is acquired, with a new window.
- At start-up an exhausted namespace is retried for `acquireTimeout` (the ttl). Past it, start-up fails.
- `storeNow` is compared with the local clock. A skew above `tolerance/2` raises `onClockSkew` (a metric, not a safety
  check).
- **There is no switch to keep issuing after the lease is lost** (P1): under any model that could duplicate ids.

Shutdown is ordered: each generator moves to a closed state and reports its exact last timestamp, then the slot is
released with it, then the stores and executors close.

## Defaults

| Setting | Default | |
|---|---|---|
| `ttl` | 30s | a crashed instance's slot is free again after at most 30s (+ `tolerance`) |
| renewal | `ttl/3` | |
| `tolerance` | 1s | per namespace (D20): the namespace records `{limit, tolerance, slotOffset}`, and other values are refused |
| `bits` | 10 | 1024 slots; at most the layout's node bits |
| `acquire-timeout` | `ttl` | how long the start waits for a free slot; 0 tries once (D42) |
| `slot-offset` | 0 | [slot offset](#slot-offset) |

## Static slots

When the deployment already guarantees a unique slot (a fixed number per host, a StatefulSet ordinal), `machine.static`
skips the store. The window then comes from a local file:

```yaml
cosid:
  machine:
    static:
      slot: ${POD_ORDINAL}
      state-file: /var/lib/cosid/state   # keeps the ceiling across restarts
      ahead: 10s
```

- Before the first id the generator writes `ceiling = now + ahead` to the file and fsyncs it. When less than `ahead/2`
  remains, it writes the next ceiling in the background.
- On restart `floor = max(ceiling in the file, not-before)`: a crash followed by a clock jump backwards cannot repeat
  an id.
- **Without `state-file` there is no protection against a clock set back across a restart.** Use it in production.
- `not-before` is the floor while the file does not exist yet. The migration tool writes it (W in the
  [migration guide](../migration/from-4x.md)).

## Named machines

A runtime has one default machine (`cosid.machine`) and any number of named ones (`cosid.machines.<name>`, or
`RuntimeSpec.Builder.machine(name, spec)` in code). Each holds one slot in a namespace of its own; a time-based
generator picks one with `machine: <name>` and issues with its slot. Rules:

- The namespaces of the started machines differ; the name `default` is reserved.
- Only the machines an enabled generator picks start, and only their stores open.
- They start one after the other (the default machine first, then by name) and are released in reverse order, each
  with the last timestamp of its own generators.
- Metrics carry a `machine` tag and the health details list each machine ([monitoring](../operations/monitoring.md)).

### Slot offset

`leased.slot-offset` (default 0) shifts the node: **node = slot-offset + slot**. The store still keeps the slots
`[0, 2^bits)`; only the id moves. It must hold that `slot-offset + 2^bits <= 2^node-bits` for every generator on the
machine. The namespace records `{limit, tolerance, slotOffset}`, and a runtime with other values is refused
(`NamespaceMismatchException`), so every user of a namespace agrees on them.

The offset keeps the nodes below it free, so 4.x instances (whose machine ids stay below it) and 5.0 instances can
issue in one namespace at once during a [rolling migration](../migration/from-4x.md). Once used, never lower it.

## Ids unique across services

Ids of one service are unique by its own namespace. For ids unique **across** services (a cosid80 token, an id several
services write into one table), give those services one named machine in a shared namespace:

```yaml
# The same in every service that shares the ids
cosid:
  namespace: ${spring.application.name}   # the default machine and the segments stay per service
  stores:
    cache: { type: redis, options: { uri: "redis://redis:6379" } }
  machine:
    leased: { store: cache }
  machines:
    global:
      namespace: global-ids
      leased: { store: cache, bits: 12 }   # 4096 slots: the peak pods of all services together, surge included
  generators:
    token: { strategy: cosid80, machine: global }
```

- Every service uses the same store, `bits`, `tolerance` and `slot-offset` for `global-ids`; the first one to start
  records them, and a different value fails the start of the next.
- `bits` counts the pods of all the services together, rolling-update surge included. A cosid80 node has 20 bits, so
  there is room to raise it.
- A restricted cosid-server token lists both namespaces.
- Clients in other languages, or with few ids, can call cosid-server's `/ids` instead of running a machine of their
  own ([server](../server.md)).
