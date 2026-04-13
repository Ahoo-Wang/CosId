# Best Practices

This guide covers recommended practices for using CosId in production environments.

## Choose the Right ID Generator

### High-Performance Scenarios: SegmentChainId

For maximum throughput with minimal latency, use `SegmentChainId`:

- TPS can reach **12,743W+/s** (approaching `AtomicLong` performance)
- P9999 latency as low as **0.208 us/op**
- Ideal for high-throughput services where order is not critical

### Clock-Sensitive Scenarios: SnowflakeId

When IDs must be time-ordered or globally unique:

- Uses 41-bit timestamp for time ordering
- Supports clock synchronization to handle clock rollback
- Ideal for systems requiring time-based ordering

### JavaScript Frontend Integration

For IDs that will be processed by JavaScript (53-bit safe integer limit):

- Use `SafeJavaScriptSnowflakeId` with reduced bit allocation
- Or convert to string using `SnowflakeFriendlyIdConverter`
- Never pass raw SnowflakeId directly to frontend

## Segment Configuration Best Practices

### Step Size Configuration

Choose `Step` based on your throughput requirements:

| Step Size | Throughput | Orderliness | Use Case |
|-----------|------------|-------------|----------|
| 1-10 | Lower | Higher | Development, low-throughput |
| 10-100 | Medium | Medium | General production |
| 100-1000 | High | Lower | High-throughput scenarios |

### Safe Distance for SegmentChainId

Configure `safeDistance` based on cluster size:

- Start with `safeDistance=10`
- Increase if you experience "starvation" (ID exhaustion before prefetch completes)
- Decrease if you need more ID orderliness

```yaml
cosid:
  segment:
    chain:
      safe-distance: 10
```

## SnowflakeId Best Practices

### Epoch Configuration

Set `epoch` close to your production deployment date:

```yaml
cosid:
  snowflake:
    epoch: 1577203200000  # 2019-12-25 00:00:00 UTC
```

This reserves more timestamp bits for future use.

### Machine ID Management

**Never hardcode machine IDs** in production. Use automatic distributors:

- `RedisMachineIdDistributor`: Best for Redis environments
- `JdbcMachineIdDistributor`: Best for JDBC environments
- `ZookeeperMachineIdDistributor`: Best for ZooKeeper environments

```yaml
cosid:
  machine:
    distributor:
      type: redis  # Automatic distribution
```

### Clock Rollback Handling

Use `ClockSyncSnowflakeId` to handle clock rollback gracefully:

```yaml
cosid:
  snowflake:
    share:
      clock-sync: true
```

## ID Converter Best Practices

### Radix62 for Short IDs

For shorter string IDs, use `Radix62IdConverter`:

```yaml
converter:
  type: radix
  radix:
    char-size: 11  # Produces ~15 character IDs
    pad-start: false
```

### Prefix/Suffix for Business Logic

Use `PrefixIdConverter` or `SuffixIdConverter` for business identification:

```yaml
converter:
  type: radix
  prefix: ORD_  # Orders will look like: ORD_2BLnPb
```

### Grouped IDs for Time-Based Reset

Use `GroupedIdSegmentDistributor` for time-based sequence reset:

```yaml
cosid:
  segment:
    provider:
      daily_id:
        group:
          by: year_month_day
          pattern: yyMMdd
        converter:
          prefix: DAILY_
```

## Monitoring and Observability

### Use Actuator Endpoints

Enable CosId actuator endpoints for monitoring:

```yaml
management:
  endpoints:
    web:
      exposure:
        include:
          - cosid
          - cosidGenerator
```

### Key Metrics to Monitor

- **segment.available**: Available IDs in current segment
- **segment.step**: Current step size
- **snowflake.lastTimestamp**: Last generated timestamp
- **distributor.nextMaxId.latency**: Segment acquisition latency
