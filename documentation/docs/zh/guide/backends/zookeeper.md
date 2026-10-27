# ZooKeeper

`cosid-store-zookeeper`：基于 Curator 5.9 的 `RangeStore` 与 `SlotLeaseStore`。**要求 ZooKeeper 3.8 或更高版本**（D27），
主要目标为 3.9；CI 在两者上都运行 TCK。3.7.2 与 3.6.4 通过过一次 TCK（不承诺支持）。3.5 及更早版本预计会失败，
因为 acquire 用只读 `multi` 读取槽位。

## 配置

```yaml
cosid:
  stores:
    zk:
      type: zookeeper
      timeout: 3s                    # 每个存储操作的整体超时，包括比较并交换的重试
      options:
        connectString: zk-1:2181,zk-2:2181,zk-3:2181
```

客户端要么是 Spring 的（或你自己的）已启动的 `CuratorFramework`，由调用方关闭；要么由存储根据选项创建并负责关闭：

| 选项 | 默认值 |
|---|---|
| `connectString` | `localhost:2181` |
| `sessionTimeout` | 60s |
| `connectionTimeout` | 15s |
| `blockUntilConnectedWait` | 10s |
| `retry.baseSleepTime` / `retry.maxRetries` / `retry.maxSleep` | 100ms / 5 / 500ms |

## 节点

| 节点 | 数据 |
|---|---|
| `/cosid/range/{key}` | 下一个值的十进制文本，封存后追加 `:sealed`；永不删除 |
| `/cosid/slot/{ns}` | 命名空间的 `limit` 与 `tolerance` |
| `/cosid/slot/{ns}/{slot}` | 以 `name=value` 行表示的槽位：owner key、incarnation、上一个 owner key、**fence**、ttl、tolerance、floor、过期时间与 ceiling 的下限、next floor |
| `/cosid/slot/{ns}/.clock` | 每次 acquire 都会写入；其 `mtime` 是过期判断所用的 `now` |
| `/cosid/owner/{ns}/{ownerKey}` | 该 key 上次持有的槽位，与每次 acquire 在同一个 `multi` 中写入 |

- **fence 存放在数据中**，而不是 `Stat.version`；version 只用于实现比较并交换。因此重建的节点无法让 fence 回退，
  运行时代码也绝不删除槽位节点。
- 租约时间取自产生它们的那次写入的 `mtime`：`expiresAt = max(expiresMin, mtime + ttl)`，
  `ceiling = max(ceilingMin, expiresAt + tolerance)`。下限来自被替换的那次写入，因此两者在一个任期内都不会回退。
- 一次 acquire 读取时钟节点与 owner 索引，然后只读取索引列出的槽位和 preferred 槽位。最小的空闲槽位按递增批次查找
  （32、64、…，每个只读 `multi` 最多 256 个槽位）。只有命名空间耗尽时才读取全部槽位。
- 命名空间不能包含 `/`；区间 key 必须是合法的 ZooKeeper 路径。

## 运维前提：重建槽位节点 {#rebuild}

::: warning 人工重建槽位节点
如果槽位节点曾被恢复或人工重建（从备份恢复、迁移 ensemble），**请在时钟不落后于原节点 `mtime` 的服务器上写入它们**。
租约时间由 `mtime` 推导，因此在落后的时钟下恢复，可能发出与上一个持有者重叠的窗口。如果无法确保这一点，
**请在恢复后等待一个 ttl（加上 tolerance）再启动任何客户端**。
:::
