---
name: cosid-manual-integration
description: Guide for manually integrating CosId into Java/Kotlin projects without Spring Boot auto-configuration. Use this skill when the user wants to configure CosId programmatically, set up custom ID generators, integrate CosId in non-Spring environments, asks about CosId core API usage (SnowflakeId, SegmentId, CosIdGenerator), needs to configure machine ID allocation manually, or mentions "manual integration", "programmatic configuration", "without Spring Boot", "non-Spring environment", or "library integration".
---

# CosId Manual Integration Skill

## Instructions

### When to Use

- User wants to integrate CosId without Spring Boot
- User needs custom programmatic configuration of ID generators
- User is building a library or framework that needs ID generation
- User asks about CosId core API usage (SnowflakeId, SegmentId, CosIdGenerator)
- User needs to configure machine ID allocation manually

### Core Architecture

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

### Machine ID Allocation

For SnowflakeId, each instance needs a unique machineId. Options:

- **Manual**: Set machineId directly (0-1023 for default 10-bit)
- **Redis**: `RedisMachineIdDistributor` - uses Redis for coordination
- **JDBC**: `JdbcMachineIdDistributor` - uses database table
- **ZooKeeper**: `ZookeeperMachineIdDistributor` - uses ZK nodes
- **MongoDB**: `MongoMachineIdDistributor` - uses MongoDB collection
- **StatefulSet**: Kubernetes StatefulSet ordinal as machineId

### Manual Configuration Pattern

```java
// 1. Create machine ID distributor
MachineIdDistributor distributor = new RedisMachineIdDistributor(redisTemplate);

// 2. Allocate machine ID
int machineId = distributor.distribute("my-namespace", instanceId, Duration.ofSeconds(10));

// 3. Create SnowflakeId
SnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);

// 4. Wrap with clock sync for safety
SnowflakeId safeId = new ClockSyncSnowflakeId(snowflakeId);

// 5. Generate IDs
long id = safeId.generate();
```

### Key Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| epoch | 2019-12-24 | Custom epoch for timestamp calculation |
| timestampBit | 41 (ms) / 31 (s) | Bits for timestamp component |
| machineBit | 10 | Bits for machine ID (max 1024 machines) |
| sequenceBit | 12 (ms) / 22 (s) | IDs per time unit per machine |
| clockSync | true | Enable clock backwards synchronization |

### Common Pitfalls

- **Clock backwards**: Always use `ClockSyncSnowflakeId` wrapper in production
- **Machine ID overflow**: With 10 bits, max 1024 instances per namespace
- **Sequence exhaustion**: High throughput may need `SecondSnowflakeId` (4M/s/machine) over millisecond variant (4K/s/machine)
- **JavaScript safety**: Use `SafeJavaScriptSnowflakeId` if IDs go to frontend

### Testing Configuration

For tests, use `ManualMachineIdDistributor` with fixed machine IDs:

```java
MachineIdDistributor distributor = new ManualMachineIdDistributor(1);
```

## References

- Source: `cosid-core/src/main/java/me/ahoo/cosid/`
- Examples: `examples/` directory
- Spring Boot starter: `cosid-spring-boot-starter/` for auto-config reference
