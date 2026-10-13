# ID 转换器

`IdConverter` 把 `long` 型 ID 转换为字符串，也能转换回来。`IdGenerator.generateAsString()` 使用生成器自己的转换器，因此同一个生成器既能生成数字主键，也能生成格式化的业务编号。

```java
public interface IdConverter {
    String asString(long id);

    default String asString(long id, GroupedKey group) {
        return asString(id);
    }

    default String asString(GroupedId groupedId) {
        return asString(groupedId.getValue(), groupedId.getGroup());
    }

    long asLong(String idString);
}
```

<p align="center">
  <img src="../../../public/assets/design/IdConverter-impl-class.png" alt="IdConverter 实现类图"/>
</p>

## 基础转换器

| 转换器 | `converter.type` | 输出 | 说明 |
|--------|------------------|------|------|
| `ToStringIdConverter` | `to_string` | 十进制，`12345` | `to-string.pad-start` 用 `0` 补齐到 `to-string.char-size` |
| `Radix62IdConverter` | `radix`（Starter 默认） | `0-9A-Za-z`，最多 11 个字符 | 区分大小写；`radix.pad-start`（默认 `true`）补齐到 `radix.char-size`（默认 `11`），使字符串排序与数字一致 |
| `Radix36IdConverter` | `radix36` | `0-9A-Z`，最多 13 个字符 | 不区分大小写；`radix36.pad-start`（默认 `true`）、`radix36.char-size`（默认 `13`） |
| `SnowflakeFriendlyIdConverter` | `snowflake_friendly` | `20210623131730192-1-0` | 仅用于 Snowflake ID：按 `cosid.snowflake.zone-id` 格式化的时间戳、机器号、序列号 |
| 自定义类 | `custom` | | `custom.type`：带公共无参构造器的 `IdConverter` 实现类 |

## 装饰器

装饰器包裹基础转换器，在其前后添加文本。Starter 按以下顺序由内向外应用：分组前缀与日期前缀（`before-prefix` 为 `true` 时，默认）、`prefix`、分组前缀与日期前缀（`before-prefix` 为 `false` 时），最后是 `suffix`。

| 转换器 | 属性 | 输出 |
|--------|------|------|
| `PrefixIdConverter` | `prefix: BIZ-` | `BIZ-12345` |
| `SuffixIdConverter` | `suffix: -BIZ` | `12345-BIZ` |
| `DatePrefixIdConverter` | `date-prefix.enabled: true`、`pattern`（默认 `yyMMdd`）、`delimiter`（默认 `-`） | `260101-12345`，生成时的日期。日期长度会变化的模式（`M`、`d` 等单字母字段，或随语言环境变化的文本月份）需要非空的 `delimiter`：没有分隔符时无法确定 ID 从哪里开始，启动时会打印警告 |
| `GroupedPrefixIdConverter` | `group-prefix.enabled: true`、`delimiter`（默认 `-`） | `20260101-12345`，分组号段 ID 的[分组](./segment.md#分组号段) |

分组前缀需要分组号段 ID（`group.by`）：在没有分组的生成器上启用它，Starter 会在启动时失败；不带分组调用 `asString(long)` 会抛出 `UnsupportedOperationException`。

## 示例

```yaml
cosid:
  segment:
    enabled: true
    provider:
      order_no:
        converter:
          type: to_string
          prefix: ORD
          to-string:
            pad-start: true
            char-size: 10
```

此时 `generateAsString()` 返回 `ORD0000000001`，`asLong("ORD0000000001")` 返回 `1`。各种常见格式的现成配置见[常用 ID 配方](../recipes.md)。
