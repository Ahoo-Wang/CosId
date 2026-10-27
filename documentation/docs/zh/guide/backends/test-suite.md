# 存储 TCK

`cosid-store-tck` 包含每个存储都要通过的契约、一个内存存储与测试工具。可以用它测试自定义的
`StoreProvider`、新的 `JdbcDialect`，或在没有数据库的情况下测试你的应用。

```kotlin
testImplementation("me.ahoo.cosid:cosid-store-tck")
```

```java
class MyRangeStoreTest extends RangeStoreContract {
    @Override
    protected RangeStore newStore() { return new MyStore(...).ranges(); }
}
class MySlotLeaseStoreTest extends SlotLeaseStoreContract {
    @Override
    protected SlotLeaseStore newStore() { return new MyStore(...).slots(); }
}
```

| 方面 | 用例 |
|---|---|
| 区间 | 第一个区间从 `initialOffset` 开始；并发首次创建只有一个胜出；N 个线程 × M 个存储实例永不重叠且严格递增；超过 2^53 的值精确；到达末尾时抛出 `RangeExhaustedException`；已封存的 key 失败并保留其值；同一连接预留不同 key（不会读到过期的 `last_insert_id`） |
| 租约基础 | 槽位选择顺序（幂等、preferred、粘性、最小）；`Exhausted`；`limit` 边界 |
| 安全性 | 被接管后 `floor ≥` 前一持有者最终的 `ceiling`；释放后 `floor ≥ lastIssued`；`ceiling` 与 `next_floor` 单调；同一 key 的另一个 incarnation 绝不会被接管；已过期持有者的续约与释放失败；续约是幂等的 |
| 时钟 | `storeNow` 来自存储；同一次写入中 `expiresAt − storeNow == ttl` |
| 并发 | N 个客户端在同一命名空间中获取到互不相同的槽位 |
| 元数据 | 不同的命名空间参数被拒绝 |

模块中还有：

- `InMemoryStore` 通过所有契约；core 与 Spring 的测试使用它，你的测试也可以使用（`memory` provider）。
- `ManualClock` 在测试中驱动存储时钟。
- 故障注入包装器可以添加延迟、超时以及会被重放的丢失响应。
- `ContractsRan` 读取 JUnit XML 报告，在契约用例被跳过时失败。每个 CosId 后端都在 `check` 中运行它，
  因此缺少数据库的 CI 任务不会悄无声息地通过。
