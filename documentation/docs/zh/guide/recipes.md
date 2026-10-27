# ID 配方

常见 ID 的现成 `cosid.*` 配置。每个配方都假定使用[快速开始](getting-started.md)中的存储与机器号槽位；
可以在一个运行时中按需组合任意多个。

## 主键

```yaml
cosid:
  generators:
    default:                       # 63 位、按时间有序的 BIGINT
      strategy: snowflake
```

每张表一个稠密、严格递增的 `BIGINT`，每个区间一次存储写入：

```yaml
    order:
      strategy: segment
      store: db
```

## 供 JavaScript 客户端使用的 ID

53 位 Snowflake 可以放进 JavaScript 的 `number`（最多 8 个槽位，因此 `machine.leased.bits: 3`）：

```yaml
cosid:
  machine:
    leased: { store: db, bits: 3 }
  generators:
    default:
      strategy: snowflake
      layout: JS_SAFE_MS
```

或者保留 63 位，用 Jackson 的 [`@AsString`](integrations/jackson.md) 以文本形式发送。

## 短文本 ID

```yaml
    short-link:
      strategy: snowflake
      codec: [ { radix: { radix: 62, width: 11, pad-start: true } } ]     # 11 个字符，可排序
    coupon:
      strategy: segment
      store: db
      codec: [ { radix: { radix: 36 } }, { prefix: "C" } ]                # C1, C2, … CA, …
```

## 可读的 trace ID

```yaml
    trace:
      strategy: snowflake
      codec: [ { friendly: { zone: Asia/Shanghai } } ]                    # 20261027153012123-0003-0017
```

`Friendly.decode`（以及 `codec.decode`）可以把它们解析回来；`Layout.unpack(id)` 给出数值 ID 的各个部分。

## 带日期的业务编号

带有签发日期、但不重置的订单号：

```yaml
    order-no:
      strategy: segment
      store: db
      codec:
        - radix: { radix: 10, width: 8, pad-start: true }
        - date-prefix: { pattern: yyyyMMdd, delimiter: "", source: NOW, zone: Asia/Shanghai }   # 2026102700000042
```

每天重新开始的单据号：

```yaml
    bill:
      strategy: periodic-segment
      store: db
      period: YEAR_MONTH_DAY
      zone: Asia/Shanghai
      seal-after: 2d
      codec:
        - date-prefix: { delimiter: "-" }
        - radix: { radix: 10, width: 6, pad-start: true }                # 20261027-000042
```

## 超过 64 位

```yaml
    token:
      strategy: cosid80                                                   # 44 位毫秒、20 位节点、16 位序列
      codec: [ { radix: { radix: 62 } } ]
```

## 秒级精度 ID

```yaml
    event:
      strategy: snowflake
      layout: SNOWFLAKE_S                                                 # 31 位秒、22 位序列
```

## `int` ID

```java
IntIdGenerator small = IdGenerators.toInt(runtime.generator("order"));   // 超过 Integer.MAX_VALUE 时抛出 IdOverflowException
```

标注了 `@CosId` 的 `int`（或 `Integer`）字段也以同样的方式做范围检查。

## 跨实例趋势递增的号段

多个实例各自持有自己的区间，因此不同实例的 ID 会交错。`range-ttl` 限定已持有的区间最多能陈旧多久：

```yaml
    order:
      strategy: segment
      store: db
      range-ttl: 1m
```

## 完全不用数据库

由部署保证唯一的槽位，且不使用存储：

```yaml
cosid:
  machine:
    static:
      slot: ${POD_ORDINAL}
      state-file: /var/lib/cosid/state
  generators:
    default:
      strategy: snowflake
```

## 供其他语言使用的 ID

以 `cosid.server.ids.enabled: true` 运行 [cosid-server](server.md)，并携带 token 调用
`GET /ids/{generator}?count=100`。
