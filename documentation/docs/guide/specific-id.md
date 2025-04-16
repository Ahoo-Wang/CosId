# 特定场景ID配置

## SnowflakeId

### snowflake_friendly

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

### snowflake_orderly_friendly

使用 _SnowflakeId_ 算法，要求输出的有序的ID字符串：

- 格式：`yyyyMMddHHmmssSSS-[0]<machineId>-[0]<sequence>`
- 例如：`20250215122059820-0000-0001`

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
      snowflake_orderly_friendly:
        converter:
          type: snowflake_friendly
          friendly:
            pad-start: true
```
```json [配置信息]
{
  "snowflake_orderly_friendly": {
    "kind": "StringSnowflakeId",
    "actual": {
      "kind": "ClockSyncSnowflakeId",
      "actual": {
        "kind": "MillisecondSnowflakeId",
        "epoch": 1577203200000,
        "timestampBit": 41,
        "machineBit": 10,
        "sequenceBit": 12,
        "machineId": 0,
        "lastTimestamp": -1,
        "converter": {
          "kind": "Radix62IdConverter",
          "radix": 62,
          "charSize": 11,
          "padStart": true,
          "maxId": 9223372036854776000,
          "actual": null
        },
        "safeJavascript": false,
        "actual": null
      },
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854776000,
        "actual": null
      }
    },
    "converter": {
      "kind": "SnowflakeFriendlyIdConverter",
      "padStart": true,
      "machineCharSize": 4,
      "sequenceCharSize": 4,
      "actual": null
    }
  }
}
```
:::

### snowflake_short_id

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

### snowflake_friendly_second

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

## SegmentId

### biz_prefix_no

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

### date_prefix_no

使用 _SegmentId_ 算法，要求输出的ID字符串：
- 格式：`<prefix><date><sequence>`
- 日期位：6位日期字符串，格式`yyMMdd`
- 例如：`BIZ-240618-25`

::: code-group
```yaml {7-14} [配置]
cosid:
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      date_prefix_no:
        converter:
          type: to_string
          prefix: BIZ-
          date-prefix:
            enabled: true
            pattern: yyMMdd
```
```json [配置信息]
{
  "date_prefix_no": {
    "kind": "StringSegmentId",
    "actual": {
      "kind": "SegmentChainId",
      "fetchTime": 1718704101,
      "maxId": 20,
      "offset": 0,
      "sequence": 0,
      "step": 20,
      "isExpired": false,
      "isOverflow": false,
      "isAvailable": true,
      "groupedKey": {
        "key": "",
        "ttlAt": 9223372036854776000
      },
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854776000
      }
    },
    "converter": {
      "kind": "PrefixIdConverter",
      "prefix": "BIZ-",
      "actual": {
        "kind": "DatePrefixIdConverter",
        "pattern": "yyMMdd",
        "actual": {
          "kind": "ToStringIdConverter",
          "padStart": false,
          "charSize": 10
        }
      }
    }
  }
}
```
:::

### no_suffix_biz

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

### biz_prefix_radix

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

### biz_prefix_radix36

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

### group_year_biz

使用 _SegmentId_ 算法，要求输出的ID字符串：
- 起始序号：`0`
- 格式：`<prefix><year><sequence>`
- 分组：按年分组，每年序号从0开始。即每年序号需要重置为0.
- 序号位：8位数值，不足8位前补0
- 例如：`BIZ-2024-00000231`

::: code-group
```yaml {7-18} [配置]
cosid:
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      group_year_biz:
        group:
          by: year
          pattern: yyyy
        converter:
          type: to_string
          to-string:
            pad-start: true
            char-size: 8
          prefix: BIZ-
          group-prefix:
            enabled: true
```
```json {14-17} [配置信息]
{
  "group_year_biz": {
    "kind": "StringSegmentId",
    "actual": {
      "kind": "SegmentChainId",
      "fetchTime": 1715911764,
      "maxId": 570,
      "offset": 550,
      "sequence": 550,
      "step": 20,
      "isExpired": false,
      "isOverflow": false,
      "isAvailable": true,
      "groupedKey": {
        "key": "2024",
        "ttlAt": 1735660799
      },
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854776000
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

### group_year_month_biz

使用 _SegmentId_ 算法，要求输出的ID字符串：
- 起始序号：`0`
- 格式：`<prefix><year_month><sequence>`
- 分组：按年月分组，序号从0开始。即跨月序号需要重置为0.
- 序号位：8位数值，不足8位前补0
- 例如：`BIZ-240516-00000061`

::: code-group
```yaml {7-18} [配置]
cosid:
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      group_year_month_biz:
        group:
          by: year_month
          pattern: yyyyMM
        converter:
          type: to_string
          to-string:
            pad-start: true
            char-size: 8
          prefix: BIZ-
          group-prefix:
            enabled: true
```
```json {14-17} [配置信息]
{
  "group_year_month_biz": {
    "kind": "StringSegmentId",
    "actual": {
      "kind": "SegmentChainId",
      "fetchTime": 1715911765,
      "maxId": 310,
      "offset": 290,
      "sequence": 290,
      "step": 20,
      "isExpired": false,
      "isOverflow": false,
      "isAvailable": true,
      "groupedKey": {
        "key": "202405",
        "ttlAt": 1717171199
      },
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854776000
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

### group_year_month_day_biz

使用 _SegmentId_ 算法，要求输出的ID字符串：
- 起始序号：`0`
- 格式：`<prefix><year_month_day><sequence>`
- 分组：按日期分组，序号从0开始。即明天序号需要重置为0.
- 序号位：8位数值，不足8位前补0
- 例如：`BIZ-240516-00000001`

::: code-group
```yaml {7-18} [配置]
cosid:
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      group_year_month_day_biz:
        group:
          by: year_month_day
          pattern: yyMMdd
        converter:
          type: to_string
          to-string:
            pad-start: true
            char-size: 8
          prefix: BIZ-
          group-prefix:
            enabled: true
```
```json {14-17} [配置信息]
{
  "group_year_month_day_biz": {
    "kind": "StringSegmentId",
    "actual": {
      "kind": "SegmentChainId",
      "fetchTime": 1715911765,
      "maxId": 280,
      "offset": 260,
      "sequence": 260,
      "step": 20,
      "isExpired": false,
      "isOverflow": false,
      "isAvailable": true,
      "groupedKey": {
        "key": "240517",
        "ttlAt": 1715961599
      },
      "converter": {
        "kind": "Radix62IdConverter",
        "radix": 62,
        "charSize": 11,
        "padStart": true,
        "maxId": 9223372036854776000
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