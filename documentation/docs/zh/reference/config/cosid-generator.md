# CosIdGenerator 配置

> `me.ahoo.cosid.spring.boot.starter.cosid.CosIdGeneratorProperties`，前缀 `cosid.generator`

`CosIdGenerator` 需要机器号：请同时设置 `cosid.machine.enabled=true`，见[机器号配置](./machine.md)。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | `boolean` | `false` | 注册 `CosIdGenerator` Bean，在 `IdGeneratorProvider` 中名为 `cosid` |
| `type` | `Type` | `radix62` | `radix62`、`radix36` 或 `friendly` |
| `namespace` | `String` | `cosid.namespace` | 该生成器机器号所在的命名空间 |
| `timestamp-bit` | `int` | `44` | 时间戳位数 |
| `machine-bit` | `int` | `20` | 机器号位数；与 `cosid.machine.machine-bit` 无关 |
| `sequence-bit` | `int` | `16` | 序列号位数 |
| `sequence-reset-threshold` | `int` | `32767` | 序列号达到该值后在下一毫秒重新开始 |
| `zone-id` | `ZoneId` | 系统默认 | `friendly`：时间戳的时区 |
| `pad-start` | `boolean` | `true` | `friendly`：用 `0` 补齐机器号和序列号 |

## 示例

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: jdbc
  generator:
    enabled: true
```
