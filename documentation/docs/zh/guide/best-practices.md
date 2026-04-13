# 最佳实践

本指南涵盖在生产环境中使用 CosId 的推荐实践。

## 选择合适的 ID 生成器

### 高性能场景：SegmentChainId

对于最大吞吐量且低延迟的场景，使用 `SegmentChainId`：

- TPS 可达 **12,743W+/s**（接近 `AtomicLong` 性能）
- P9999 延迟低至 **0.208 us/op**
- 适用于对顺序要求不高的海量吞吐服务

### 时钟敏感场景：SnowflakeId

当 ID 需要按时间排序或全局唯一时：

- 使用 41 位时间戳保证时间有序
- 支持时钟同步以处理时钟回拨
- 适用于需要基于时间的排序系统

### JavaScript 前端集成

对于需要由 JavaScript 处理的 ID（53 位安全整数限制）：

- 使用减少位数分配的 `SafeJavaScriptSnowflakeId`
- 或使用 `SnowflakeFriendlyIdConverter` 转换为字符串
- 切勿将原始 SnowflakeId 直接传递给前端

## 号段配置最佳实践

### Step 大小配置

根据吞吐量需求选择 `Step`：

| Step 大小 | 吞吐量 | 有序性 | 适用场景 |
|-----------|--------|--------|----------|
| 1-10 | 较低 | 较高 | 开发、低吞吐 |
| 10-100 | 中等 | 中等 | 一般生产环境 |
| 100-1000 | 高 | 较低 | 高吞吐场景 |

### SegmentChainId 安全距离

根据集群规模配置 `safeDistance`：

- 从 `safeDistance=10` 开始
- 如果遇到"饥饿"（预取完成前 ID 耗尽）则增加
- 如果需要更高的 ID 有序性则减少

```yaml
cosid:
  segment:
    chain:
      safe-distance: 10
```

## SnowflakeId 最佳实践

### Epoch 配置

将 `epoch` 设置为接近生产部署日期：

```yaml
cosid:
  snowflake:
    epoch: 1577203200000  # 2019-12-25 00:00:00 UTC
```

这可以为未来使用保留更多时间戳位。

### 机器 ID 管理

**切勿在生产环境中硬编码机器 ID**。使用自动分发器：

- `RedisMachineIdDistributor`: 适用于 Redis 环境
- `JdbcMachineIdDistributor`: 适用于 JDBC 环境
- `ZookeeperMachineIdDistributor`: 适用于 ZooKeeper 环境

```yaml
cosid:
  machine:
    distributor:
      type: redis  # 自动分发
```

### 时钟回拨处理

使用 `ClockSyncSnowflakeId` 优雅处理时钟回拨：

```yaml
cosid:
  snowflake:
    share:
      clock-sync: true
```

## ID 转换器最佳实践

### Radix62 短 ID

对于较短的字符串 ID，使用 `Radix62IdConverter`：

```yaml
converter:
  type: radix
  radix:
    char-size: 11  # 生成约 15 个字符的 ID
    pad-start: false
```

### 前缀/后缀用于业务标识

使用 `PrefixIdConverter` 或 `SuffixIdConverter` 进行业务识别：

```yaml
converter:
  type: radix
  prefix: ORD_  # 订单将显示为：ORD_2BLnPb
```

### 分组 ID 用于基于时间的重置

使用 `GroupedIdSegmentDistributor` 实现基于时间的序号重置：

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

## 监控和可观测性

### 使用 Actuator 端点

启用 CosId actuator 端点进行监控：

```yaml
management:
  endpoints:
    web:
      exposure:
        include:
          - cosid
          - cosidGenerator
```

### 需要监控的关键指标

- **segment.available**: 当前号段可用 ID 数
- **segment.step**: 当前 step 大小
- **snowflake.lastTimestamp**: 最后生成的时间戳
- **distributor.nextMaxId.latency**: 号段获取延迟
