---
name: cosid-strategy-guide
description: Guide for choosing the right CosId ID generation strategy. Use this skill whenever the user is unsure which CosId ID generator to use, asks about SnowflakeId vs SegmentId vs SegmentChainId vs CosIdGenerator, or needs help selecting a distributed ID strategy. Also use when the user mentions ID throughput, monotonic IDs, time-ordered IDs, machine ID allocation, or asks "which ID generator should I use" in the context of distributed systems or CosId.
---

# CosId ID Strategy Selection Guide

CosId provides 4 ID generation strategies, each designed for different use cases. This guide helps you choose the right one.

## Strategy Comparison

| | CosIdGenerator | SnowflakeId | SegmentId | SegmentChainId |
|---|---|---|---|---|
| **Throughput** | ~15M+/s | ~4M+/s | ~20M+/s | ~127M+/s |
| **ID Type** | String only | long + String | long + String | long + String |
| **Trend** | Time-ordered | Time-ordered | Monotonic increasing | Monotonic increasing |
| **External Dependency** | MachineIdDistributor | MachineIdDistributor | IdSegmentDistributor | IdSegmentDistributor |
| **Coordination** | Once at startup | Once at startup | Per segment | Background prefetch |
| **Clock Sensitive** | Yes | Yes | No | No |
| **Max Instances** | 2^machineBit | 2^machineBit | Unlimited | Unlimited |

## Decision Tree

```
Need distributed IDs?
└── Yes
    ├── Need time-sortable long IDs?
    │   └── Yes → SnowflakeId (max instances limited by machineBit, typically 1024)
    └── No time-sortable requirement
        ├── Large-scale cluster (超过 SnowflakeId 实例限制)?
        │   └── Yes → CosIdGenerator (String IDs, 全局唯一, 支持超大规模集群)
        └── Need maximum throughput?
            └── Yes → SegmentChainId
            └── No → SegmentId (simpler)
```

## Strategy Details

### 1. CosIdGenerator — Large-Scale Cluster String IDs

A state-based ID generator designed for large-scale clusters that exceed SnowflakeId's machine bit limit. Produces compact string IDs with globally unique guarantees. Like SnowflakeId, it requires a `MachineIdDistributor` to assign unique machine IDs to each instance, but it is not constrained by the 63-bit long format, allowing it to support a much larger number of instances.

**When to use:**
- Large-scale clusters that exceed SnowflakeId's machine bit limit (typically 1024 instances)
- Need globally unique IDs across a very large number of instances
- Want compact string IDs (shorter than long-to-string converted SnowflakeIds)
- Don't need `long` type IDs (only generates String)

**Tradeoffs:**
- Cannot generate `long` IDs — only `String` via `generateAsString()`
- Requires `MachineIdDistributor` for machine ID allocation (same as SnowflakeId)
- Clock-sensitive (like SnowflakeId)

**Variants:**
- `Radix62CosIdGenerator` — Base62 encoding, compact (default)
- `Radix36CosIdGenerator` — Base36 encoding, uppercase only
- `FriendlyCosIdGenerator` — Human-readable format
- `ClockSyncCosIdGenerator` — Wraps any CosIdGenerator with clock sync

**Spring Boot config:**

```yaml
cosid:
  machine:
    enabled: true
    distributor:
      type: redis  # or jdbc, mongo, zookeeper, manual, stateful_set
  generator:
    enabled: true
```

**Programmatic usage:**

```java
CosIdGenerator generator = new Radix62CosIdGenerator(machineId);
String id = generator.generateAsString();
```

### 2. SnowflakeId — Time-Ordered Distributed IDs

The Twitter Snowflake algorithm. Generates 63-bit `long` IDs composed of timestamp + machineId + sequence.

**When to use:**
- Need time-sortable IDs (IDs roughly reflect creation time)
- Want `long` type IDs for database primary keys
- Need to parse IDs back into timestamp/machineId/sequence components
- JavaScript clients need safe IDs (use `SafeJavaScriptSnowflakeId`)

**Bit layout (default MillisecondSnowflakeId):**

```
| 1 bit unused | 41 bits timestamp | 10 bits machineId | 12 bits sequence |
```

- **41-bit timestamp** — ~69 years from epoch (default epoch: 2020-01-01)
- **10-bit machineId** — up to 1024 instances
- **12-bit sequence** — 4096 IDs per millisecond

**Customizing bit layout:**

```yaml
cosid:
  snowflake:
    provider:
      second_based:
        timestamp-unit: second
        epoch: 1577203200      # 2020-01-01 as Unix seconds
        timestamp-bit: 31
        machine-bit: 10
        sequence-bit: 22
```

**Important constraint:** `timestampBit + machineBit + sequenceBit = 63` (63 because the sign bit is reserved).

**JavaScript-safe IDs:** JavaScript's `Number.MAX_SAFE_INTEGER` is 2^53 - 1. If your SnowflakeId exceeds this, use `SafeJavaScriptSnowflakeId` to constrain the bit layout automatically.

**Clock backwards handling:** SnowflakeId is sensitive to system clock changes. CosId provides `ClockSyncSnowflakeId` that wraps any SnowflakeId with clock drift tolerance:
- Small drift (< `spinThreshold`): Spin-wait until time catches up
- Large drift (> `brokenThreshold`): Throw `ClockTooManyBackwardsException`

**Spring Boot config:**

```yaml
cosid:
  machine:
    enabled: true
    distributor:
      type: redis  # or jdbc, mongo, zookeeper, manual, stateful_set
  snowflake:
    enabled: true
```

### 3. SegmentId — Monotonic IDs with Batch Allocation

Allocates IDs in contiguous segments (batches) to reduce network I/O. Each instance gets a range of IDs and serves them locally.

**When to use:**
- Need monotonic (always increasing) IDs
- Want database-friendly sequential IDs
- Simpler than SegmentChainId, good for moderate throughput
- Don't want to deal with machine ID allocation (not clock-sensitive)

**How it works:**

```
Instance A: [1-100]   ← allocated from Redis/DB
Instance B: [101-200] ← allocated from Redis/DB

Instance A serves IDs 1, 2, 3, ..., 100
When exhausted, allocates next segment [201-300]
```

**Spring Boot config:**

```yaml
cosid:
  segment:
    enabled: true
    mode: segment     # basic segment mode
    distributor:
      type: redis
    provider:
      user_id:
        step: 100     # segment size (IDs per allocation)
```

**Tuning the `step` parameter:**
- Larger step → fewer network calls, but more ID gaps on restart
- Smaller step → fewer wasted IDs, but more coordination overhead
- Start with `step: 100` for moderate workloads, increase to `1000+` for high QPS

### 4. SegmentChainId — Maximum Throughput (Recommended)

An enhancement of SegmentId that chains multiple segments with lock-free prefetching. This is the highest-throughput ID generator in CosId.

**When to use:**
- Production workloads needing maximum throughput (~127M+ IDs/s)
- Want monotonic IDs without the latency spikes of SegmentId
- Have Redis, JDBC, MongoDB, or ZooKeeper available
- The default recommended choice for most production systems

**How it differs from SegmentId:**

SegmentChainId maintains a chain of prefetched segments. A background worker prefetches new segments before the current one is exhausted, so ID generation never blocks waiting for network I/O.

```
SegmentId:      [current segment] → BLOCK until next segment allocated
SegmentChainId: [current] → [prefetched] → [prefetched] → (background prefetch)
```

The prefetch distance adapts dynamically:
- **High demand** (hungry): Distance doubles (up to 100M)
- **Low demand** (full): Distance halves (down to `safeDistance`)

**Spring Boot config:**

```yaml
cosid:
  segment:
    enabled: true
    mode: chain       # ← chain mode enables SegmentChainId
    distributor:
      type: redis
    chain:
      safe-distance: 2            # minimum prefetched segments
      prefetch-worker:
        prefetch-period: 1000ms   # how often to check prefetch
        core-pool-size: 8         # prefetch thread pool size
    provider:
      order_id:
        step: 100
```

## Distributor Backends

Both SegmentId and SegmentChainId require a distributor to coordinate segment allocation. Machine ID allocation (for SnowflakeId/CosIdGenerator) also uses distributors.

| Backend | Segment Distributor | Machine ID Distributor | Best For |
|---|---|---|---|
| **Redis** | Yes | Yes | Most common, low latency |
| **JDBC** | Yes | Yes | When only a database is available |
| **MongoDB** | Yes | Yes | MongoDB-based infrastructure |
| **ZooKeeper** | Yes | Yes | Existing ZooKeeper clusters |
| **Proxy** | Yes | Yes | Dedicated ID service architecture |
| **Manual** | No | Yes | Fixed instances, known machine IDs |
| **StatefulSet** | No | Yes | Kubernetes StatefulSet |

## Machine ID Allocation

SnowflakeId and CosIdGenerator require unique machine IDs. The `MachineIdDistributor` allocates and guards these IDs.

### How It Works

1. On startup, an instance requests a machine ID from the distributor
2. The guarder sends periodic heartbeats to maintain the lease
3. If an instance dies, its machine ID is released after `safeGuardDuration`
4. State persists locally to improve restart stability

### Choosing a Machine ID Distributor

- **Redis** (recommended): Fast, atomic allocation, works with existing Redis
- **JDBC**: Use when you already have a database but no Redis
- **Manual**: For fixed instances where you assign machine IDs yourself
- **StatefulSet**: For Kubernetes StatefulSet (uses pod ordinal as machine ID)
- **ZooKeeper**: For environments already running ZooKeeper

### Machine State Persistence

CosId persists machine state to the local filesystem to improve restart stability. A restarted instance tries to reclaim its previous machine ID before requesting a new one.

```yaml
cosid:
  machine:
    state-storage:
      local:
        state-location: .cosid-machine-state  # default
```

## Common Patterns

### Production Microservice with Redis

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis
  segment:
    enabled: true
    mode: chain
    distributor:
      type: redis
    share:
      enabled: true
    provider:
      order_id:
        converter:
          type: radix
          prefix: ORD
  snowflake:
    enabled: true
    share:
      enabled: true
```

### Database-Only with JDBC

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: jdbc
  segment:
    enabled: true
    mode: chain
    distributor:
      type: jdbc
      jdbc:
        enable-auto-init-cosid-table: true
        enable-auto-init-id-segment: true
```

### Kubernetes StatefulSet

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: stateful_set
  snowflake:
    enabled: true
  segment:
    enabled: true
    mode: chain
    distributor:
      type: redis
```
