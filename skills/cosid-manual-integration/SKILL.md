---
name: cosid-manual-integration
description: Manually integrate CosId into Java or Kotlin applications without Spring Boot auto-configuration. Use when the user needs programmatic setup for SnowflakeId, SegmentId, SegmentChainId, CosIdGenerator, machine ID distribution, custom IdConverter wiring, non-Spring environments, library/framework integration, or production-safe generator lifecycle management.
---

# CosId Manual Integration Skill

Use this skill when CosId must be configured with code instead of `cosid-spring-boot-starter`.

## Workflow

1. Identify the generator strategy. Use `$cosid-strategy-guide` first if the user has not chosen one.
2. Identify the coordination mechanism: manual machine ID, StatefulSet ordinal, Redis, JDBC, MongoDB, ZooKeeper, or proxy.
3. Show the minimal constructor/wiring path for the chosen generator.
4. Include lifecycle handling for distributors, guard/heartbeat, prefetch workers, and state storage when relevant.
5. Add a small verification example: uniqueness, monotonicity, parser behavior, or restart behavior.

## Core APIs

CosId provides three main ID generation strategies:

1. **SnowflakeId** - 64-bit time-sortable IDs (timestamp + machineId + sequence)
   - `MillisecondSnowflakeId` - millisecond precision, 41-bit timestamp, 10-bit machine, 12-bit sequence
   - `SecondSnowflakeId` - second precision, 31-bit timestamp, 10-bit machine, 22-bit sequence
   - `SafeJavaScriptSnowflakeId` - wrapper constraining to 53 bits for JS compatibility

2. **SegmentId** - Batch ID allocation for high throughput
   - `SegmentChainId` - lock-free chain with prefetch worker (~127M+ ops/s)
   - Requires a `SegmentIdDistributor` (Redis, JDBC, ZooKeeper, MongoDB)

3. **CosIdGenerator** - Large-scale cluster string ID generator (~15M+ ops/s)
   - Uses Radix62 encoding for compact string IDs
   - Requires `MachineIdDistributor` for machine ID allocation (same as SnowflakeId)
   - Supports much larger instance counts than SnowflakeId (not constrained by 63-bit long format)

## Machine ID Allocation

For SnowflakeId, each instance needs a unique machineId. Options:

- **Manual**: Set machineId directly (0-1023 for default 10-bit)
- **Redis**: `SpringRedisMachineIdDistributor` - uses Redis for coordination
- **JDBC**: `JdbcMachineIdDistributor` - uses database table
- **ZooKeeper**: `ZookeeperMachineIdDistributor` - uses ZK nodes
- **MongoDB**: `MongoMachineIdDistributor` - uses MongoDB collection
- **StatefulSet**: Kubernetes StatefulSet ordinal as machineId

Always define the namespace and instance identity deliberately. For production, prefer a distributor that can guard ownership and reclaim expired machine IDs.

## SnowflakeId Configuration Pattern

```java
// 1. Create a backend-specific MachineIdDistributor
MachineIdDistributor distributor = createMachineIdDistributor();

// 2. Allocate machine ID
int machineBit = 10;
InstanceId instanceId = InstanceId.of("order-service-0", true);
MachineState machineState = distributor.distribute(
    "my-namespace",
    machineBit,
    instanceId,
    Duration.ofSeconds(10)
);
int machineId = machineState.getMachineId();

// 3. Create SnowflakeId
SnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);

// 4. Wrap with clock sync for safety
SnowflakeId safeId = new ClockSyncSnowflakeId(snowflakeId);

// 5. Generate IDs
long id = safeId.generate();
```

If the user has fixed deployment slots, use `ManualMachineIdDistributor` or directly provide the machine ID, but warn that duplicate machine IDs can create duplicate IDs.

## Segment Configuration Pattern

Use `SegmentId` or `SegmentChainId` when monotonic IDs and batch allocation are more important than time-encoded IDs.

```java
IdSegmentDistributor distributor = createIdSegmentDistributor("order_id", 100);
SegmentChainId idGenerator = new SegmentChainId(distributor);

long id = idGenerator.generate();
String text = idGenerator.generateAsString();
```

For `SegmentChainId`, ensure the prefetch worker lifecycle is owned by the application and is closed during shutdown if the concrete setup exposes close/shutdown behavior.

## CosIdGenerator Pattern

Use `CosIdGenerator` when callers need compact string IDs rather than `long` IDs.

```java
CosIdGenerator generator = new Radix62CosIdGenerator(machineId);
String id = generator.generateAsString();
```

Wrap with the clock-sync variant when system clock drift is a production concern.

## Key Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| epoch | 2019-12-24 | Custom epoch for timestamp calculation |
| timestampBit | 41 (ms) / 31 (s) | Bits for timestamp component |
| machineBit | 10 | Bits for machine ID (max 1024 machines) |
| sequenceBit | 12 (ms) / 22 (s) | IDs per time unit per machine |
| clockSync | true | Enable clock backwards synchronization |

## Common Pitfalls

- **Clock backwards**: Always use `ClockSyncSnowflakeId` wrapper in production
- **Machine ID overflow**: With 10 bits, max 1024 instances per namespace
- **Sequence exhaustion**: High throughput may need `SecondSnowflakeId` (4M/s/machine) over millisecond variant (4K/s/machine)
- **JavaScript safety**: Use `SafeJavaScriptSnowflakeId` if IDs go to frontend
- **Lifecycle leaks**: Close distributor clients and background workers when the application shuts down
- **State loss**: Persist machine state when restart stability matters

## Testing Configuration

For tests, use `ManualMachineIdDistributor` with fixed machine IDs:

```java
MachineIdDistributor distributor = new ManualMachineIdDistributor(1);
```

Add tests for the property that matters in the user's case:

- Uniqueness across concurrent generation
- Local monotonicity for SegmentId/SegmentChainId
- Time parser correctness for SnowflakeId
- JavaScript-safe range or string conversion
- Machine ID conflict behavior when manual IDs are used

## Source Pointers

- Source: `cosid-core/src/main/java/me/ahoo/cosid/`
- Examples: `examples/` directory
- Spring Boot starter: `cosid-spring-boot-starter/` for auto-config reference
