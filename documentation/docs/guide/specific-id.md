# 特定场景ID配置

## snowflake_friendly

使用 _SnowflakeId_ 算法，要求输出的ID字符串：

- 格式：`yyyyMMddHHmmssSSS-<machineId>-<sequence>`
- 例如：`20240103152415876-5-16`

::: code-group
```yaml {9-11} [配置]
cosid:
  machine:
    enabled: true
    distributor:
      type: redis
  snowflake:
    enabled: true
    provider:
      snowflake_friendly:
        converter:
          type: snowflake_friendly
```
```json [配置信息]
{
  "snowflake_friendly": {
    "kind": "DefaultSnowflakeFriendlyId",
    "actual": {
      "kind": "ClockSyncSnowflakeId",
      "actual": {
        "kind": "MillisecondSnowflakeId",
        "epoch": 1577203200000,
        "timestampBit": 41,
        "machineBit": 10,
        "sequenceBit": 12,
        "isSafeJavascript": false,
        "machineId": 5,
        "lastTimestamp": -1,
        "converter": {
          "kind": "Radix62IdConverter",
          "radix": 62,
          "charSize": 11,
          "padStart": true,
          "maxId": 9223372036854775807
        }
      },
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854775807
      }
    },
    "converter": {
      "kind": "SnowflakeFriendlyIdConverter",  // [!code focus]
      "actual": null
    }
  }
}
```
:::

## snowflake_short_id

使用 _SnowflakeId_ 算法，要求输出的ID字符串：

- 格式：尽可能短
- 例如：`0dMszf3Ht1l`

::: code-group
```yaml {9-14} [配置]
cosid:
  machine:
    enabled: true
    distributor:
      type: redis
  snowflake:
    enabled: true
    provider:
      snowflake_short_id:
        converter:
          type: radix
          radix:
            char-size: 11
            pad-start: true
```
```json [配置信息]
{
  "snowflake_short_id": {
    "kind": "DefaultSnowflakeFriendlyId",
    "actual": {
      "kind": "ClockSyncSnowflakeId",
      "actual": {
        "kind": "MillisecondSnowflakeId",
        "epoch": 1577203200000,
        "timestampBit": 41,
        "machineBit": 10,
        "sequenceBit": 12,
        "isSafeJavascript": false,
        "machineId": 5,
        "lastTimestamp": -1,
        "converter": {
          "kind": "Radix62IdConverter",
          "radix": 62,
          "charSize": 11,
          "padStart": true,
          "maxId": 9223372036854775807
        }
      },
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854775807
      }
    },
    "converter": {
      "kind": "Radix62IdConverter",
      "radix": 62,
      "charSize": 11,
      "padStart": true,
      "maxId": 9223372036854775807
    }
  }
}
```
:::

## snowflake_friendly_second

使用 _SnowflakeId_ 算法，要求输出的ID字符串：

- 格式：`yyyyMMddHHmmss-<machineId>-<sequence>`
- 例如：`20240103153900-5-4`

::: code-group
```yaml {9-16} [配置]
cosid:
  machine:
    enabled: true
    distributor:
      type: redis
  snowflake:
    enabled: true
    provider:
      snowflake_friendly_second:
        timestamp-unit: second
        epoch: 1577203200
        timestamp-bit: 31
        machine-bit: 10
        sequence-bit: 22
        converter:
          type: snowflake_friendly
```
```json [配置信息]
{
  "snowflake_friendly_second": {
    "kind": "DefaultSnowflakeFriendlyId",
    "actual": {
      "kind": "ClockSyncSnowflakeId",
      "actual": {
        "kind": "SecondSnowflakeId",
        "epoch": 1577203200,
        "timestampBit": 31,
        "machineBit": 10,
        "sequenceBit": 22,
        "isSafeJavascript": false,
        "machineId": 5,
        "lastTimestamp": 1704265875,
        "converter": {
          "kind": "Radix62IdConverter",
          "radix": 62,
          "charSize": 11,
          "padStart": true,
          "maxId": 9223372036854775807
        }
      },
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854775807
      }
    },
    "converter": {
      "kind": "SnowflakeFriendlyIdConverter",
      "actual": null
    }
  }
}
```
:::

## biz_prefix_no

使用 _SegmentId_ 算法，要求输出的ID字符串：
- 起始序号：`2000000000`
- 格式：`<prefix><sequence>`
- 序号位：10位数值，不足10位前补0
- 例如：`BIZ2000000219`

::: code-group
```yaml {7-14} [配置]
cosid:
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      biz_prefix_no:
        offset: 2000000000
        converter:
          type: to_string
          prefix: BIZ
          to-string:
            char-size: 10
            pad-start: true
```
```json [配置信息]
{
  "biz_prefix_no": {
    "kind": "StringSegmentId",
    "actual": {
      "kind": "SegmentChainId",
      "fetchTime": 1704265844,
      "maxId": 2000000220,
      "offset": 2000000200,
      "sequence": 2000000218,
      "step": 20,
      "isExpired": false,
      "isOverflow": false,
      "isAvailable": true,
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854775807
      }
    },
    "converter": {
      "kind": "PrefixIdConverter",
      "prefix": "BIZ",
      "actual": {
        "kind": "ToStringIdConverter",
        "padStart": true,
        "charSize": 10
      }
    }
  }
}
```
:::

## no_suffix_biz

使用 _SegmentId_ 算法，要求输出的ID字符串：
- 起始序号：`2000000000`
- 格式：`<sequence><suffix>`
- 序号位：10位数值，不足10位前补0
- 例如：`2000000201BIZ`

::: code-group
```yaml {7-14} [配置]
cosid:
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      no_suffix_biz:
        offset: 2000000000
        converter:
          type: to_string
          suffix: BIZ
          to-string:
            char-size: 10
            pad-start: true
```
```json [配置信息]
{
  "no_suffix_biz": {
    "kind": "StringSegmentId",
    "actual": {
      "kind": "SegmentChainId",
      "fetchTime": 1704265915,
      "maxId": 2000000210,
      "offset": 2000000200,
      "sequence": 2000000200,
      "step": 10,
      "isExpired": false,
      "isOverflow": false,
      "isAvailable": true,
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854775807
      }
    },
    "converter": {
      "kind": "SuffixIdConverter",
      "suffix": "BIZ",
      "actual": {
        "kind": "ToStringIdConverter",
        "padStart": true,
        "charSize": 10
      }
    }
  }
}
```
:::

## biz_prefix_radix

使用 _SegmentId_ 算法，要求输出的ID字符串：
- 起始序号：`2000000000`
- 格式：`<prefix><sequence>`
- 序号位：6位62进制字符串，不足6位前补0
- 例如：`BIZ2BLnPb`

::: code-group
```yaml {7-14} [配置]
cosid:
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      biz_prefix_radix:
        offset: 2000000000
        converter:
          type: radix
          prefix: BIZ
          radix:
            char-size: 6
            pad-start: true
```
```json [配置信息]
{
  "biz_prefix_radix": {
    "kind": "StringSegmentId",
    "actual": {
      "kind": "SegmentChainId",
      "fetchTime": 1704265844,
      "maxId": 2000000200,
      "offset": 2000000180,
      "sequence": 2000000190,
      "step": 20,
      "isExpired": false,
      "isOverflow": false,
      "isAvailable": true,
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854775807
      }
    },
    "converter": {
      "kind": "PrefixIdConverter",
      "prefix": "BIZ",
      "actual": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 6,
        "padStart": true,
        "maxId": 56800235584
      }
    }
  }
}
```
:::

## biz_prefix_radix36

使用 _SegmentId_ 算法，要求输出的ID字符串：
- 起始序号：`2000000000`
- 格式：`<prefix><sequence>`
- 序号位：8位36进制字符串，不足8位前补0
- 例如：`BIZ00000044`

::: code-group
```yaml {7-14} [配置]
cosid:
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      biz_prefix_radix:
        offset: 2000000000
        converter:
          type: radix
          prefix: BIZ
          radix:
            char-size: 6
            pad-start: true
```
```json [配置信息]
{
  "biz_prefix_radix36": {
    "kind": "StringSegmentId",
    "actual": {
      "kind": "SegmentChainId",
      "fetchTime": 1704265844,
      "maxId": 150,
      "offset": 130,
      "sequence": 147,
      "step": 20,
      "isExpired": false,
      "isOverflow": false,
      "isAvailable": true,
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854775807
      }
    },
    "converter": {
      "kind": "PrefixIdConverter",
      "prefix": "BIZ",
      "actual": {
        "kind": "Radix36IdConverter",
        "radix": 36,
        "charSize": 8,
        "padStart": true,
        "maxId": 2821109907456
      }
    }
  }
}
```
:::

## group_year_biz

使用 _SegmentId_ 算法，要求输出的ID字符串：
- 起始序号：`0`
- 格式：`<prefix><year><sequence>`
- 分组：按年分组，每年序号从0开始。即每年序号需要重置为0.
- 序号位：8位数值，不足8位前补0
- 例如：`BIZ-2024-00000231`

::: code-group
```yaml {7-17} [配置]
cosid:
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      group_year_biz:
        group:
          by: year
        converter:
          type: to_string
          to-string:
            pad-start: true
            char-size: 8
          prefix: BIZ-
          group-prefix:
            enabled: true
```
```json [配置信息]
{
  "group_year_biz": {
    "kind": "StringSegmentId",
    "actual": {
      "kind": "SegmentChainId",
      "fetchTime": 1704265845,
      "maxId": 240,
      "offset": 220,
      "sequence": 230,
      "step": 20,
      "isExpired": false,
      "isOverflow": false,
      "isAvailable": true,
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854775807
      }
    },
    "converter": {
      "kind": "PrefixIdConverter",
      "prefix": "BIZ-",
      "actual": {
        "kind": "GroupedPrefixIdConverter",
        "delimiter": "-",
        "actual": {
          "kind": "ToStringIdConverter",
          "padStart": true,
          "charSize": 8
        }
      }
    }
  }
}
```
:::

## 百万级规模集群实例的全局ID

使用 _CosIdGenerator_ 算法，要求支持百万级规模集群实例的全局ID生成器。

::: code-group
```yaml {6-7} [配置]
cosid:
  machine:
    enabled: true
    distributor:
      type: redis
  generator:
    enabled: true
```
```json [配置信息]
{
  "cosid": {
    "kind": "ClockSyncCosIdGenerator",
    "actual": {
      "kind": "Radix62CosIdGenerator",
      "machineId": 5,
      "lastTimestamp": 1704265904677,
      "converter": {
        "kind": "RadixCosIdStateParser",
        "actual": null
      }
    },
    "converter": {
      "kind": "RadixCosIdStateParser",
      "actual": null
    }
  }
}
```
:::