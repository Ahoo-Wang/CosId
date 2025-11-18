# Machine 配置

> `me.ahoo.cosid.spring.boot.starter.cosid.CosIdGeneratorProperties`

| 名称            | 数据类型      | 说明                                | 默认值       |
|---------------|-----------|-----------------------------------|-----------|
| enabled       | `boolean` | 是否启用                              | `false`   |
| type          | `enum`    | 类型：`RADIX62`/`RADIX36`/`FRIENDLY` | `RADIX62` |
| namespace     | `String`  | 命名空间                              | `cosid`   |
| timestamp-bit | `int`     | 时间戳位数                             | `44`      |
| machine-bit   | `int`     | 机器位数                              | `20`      |
| sequence-bit  | `int`     | 序列位数                              | `16`      |


## 配置案例

```yaml {7-8}
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: jdbc
  generator:
    enabled: true
```
