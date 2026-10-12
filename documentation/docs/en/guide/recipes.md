# Specific Scenario ID Configuration

## SnowflakeId

### snowflake_friendly

Using the _SnowflakeId_ algorithm, the output ID string requires:

- Format: `yyyyMMddHHmmssSSS-<machineId>-<sequence>`
- For example: `20240103152415876-5-16`

::: code-group
```yaml {9-11} [Configuration]
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
```json [Configuration Information]
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

Using the _SnowflakeId_ algorithm, the output ordered ID string requires:

- Format: `yyyyMMddHHmmssSSS-[0]<machineId>-[0]<sequence>`
- For example: `20250215122059820-0000-0001`

::: code-group
```yaml {9-14} [Configuration]
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
```json [Configuration Information]
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

Using the _SnowflakeId_ algorithm, the output ID string requires:

- Format: As short as possible
- For example: `0dMszf3Ht1l`

::: code-group
```yaml {9-14} [Configuration]
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
```json [Configuration Information]
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

Using the _SnowflakeId_ algorithm, the output ID string requires:

- Format: `yyyyMMddHHmmss-<machineId>-<sequence>`
- For example: `20240103153900-5-4`

::: code-group
```yaml {9-16} [Configuration]
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
```json [Configuration Information]
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

Using the _SegmentId_ algorithm, the output ID string requires:
- Starting sequence: `2000000000`
- Format: `<prefix><sequence>`
- Sequence digits: 10-digit number, padded with 0 if less than 10 digits
- For example: `BIZ2000000219`

::: code-group
```yaml {7-14} [Configuration]
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
```json [Configuration Information]
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

Using the _SegmentId_ algorithm, the output ID string requires:
- Format: `<prefix><date><sequence>`
- Date digits: 6-digit date string, format `yyMMdd`
- For example: `BIZ-240618-25`

::: code-group
```yaml {7-14} [Configuration]
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
```json [Configuration Information]
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

Using the _SegmentId_ algorithm, the output ID string requires:
- Starting sequence: `2000000000`
- Format: `<sequence><suffix>`
- Sequence digits: 10-digit number, padded with 0 if less than 10 digits
- For example: `2000000201BIZ`

::: code-group
```yaml {7-14} [Configuration]
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
```json [Configuration Information]
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

Using the _SegmentId_ algorithm, the output ID string requires:
- Starting sequence: `2000000000`
- Format: `<prefix><sequence>`
- Sequence digits: 6-digit base62 string, padded with 0 if less than 6 digits
- For example: `BIZ2BLnPb`

::: code-group
```yaml {7-14} [Configuration]
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
```json [Configuration Information]
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

Using the _SegmentId_ algorithm, the output ID string requires:
- Starting sequence: `2000000000`
- Format: `<prefix><sequence>`
- Sequence digits: 8-digit base36 string, padded with 0 if less than 8 digits
- For example: `BIZ00000044`

::: code-group
```yaml {7-14} [Configuration]
cosid:
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      biz_prefix_radix36:
        offset: 2000000000
        converter:
          type: radix36
          prefix: BIZ
          radix:
            char-size: 6
            pad-start: true
```
```json [Configuration Information]
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

Using the _SegmentId_ algorithm, the output ID string requires:
- Starting sequence: `0`
- Format: `<prefix><year><sequence>`
- Grouping: Grouped by year, sequence starts from 0 each year. That is, the sequence needs to reset to 0 every year.
- Sequence digits: 8-digit number, padded with 0 if less than 8 digits
- For example: `BIZ-2024-00000231`

::: code-group
```yaml {7-18} [Configuration]
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
```json {14-17} [Configuration Information]
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

Using the _SegmentId_ algorithm, the output ID string requires:
- Starting sequence: `0`
- Format: `<prefix><year_month><sequence>`
- Grouping: Grouped by year and month, sequence starts from 0. That is, the sequence needs to reset to 0 across months.
- Sequence digits: 8-digit number, padded with 0 if less than 8 digits
- For example: `BIZ-240516-00000061`

::: code-group
```yaml {7-18} [Configuration]
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
```json {14-17} [Configuration Information]
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

Using the _SegmentId_ algorithm, the output ID string requires:
- Starting sequence: `0`
- Format: `<prefix><year_month_day><sequence>`
- Grouping: Grouped by date, sequence starts from 0. That is, the sequence needs to reset to 0 tomorrow.
- Sequence digits: 8-digit number, padded with 0 if less than 8 digits
- For example: `BIZ-240516-00000001`

::: code-group
```yaml {7-18} [Configuration]
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
```json {14-17} [Configuration Information]
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

## Global ID for Million-Scale Cluster Instances

Using the _CosIdGenerator_ algorithm, it requires supporting global ID generators for million-scale cluster instances.

::: code-group
```yaml {6-7} [Configuration]
cosid:
  machine:
    enabled: true
    distributor:
      type: redis
  generator:
    enabled: true
```
```json [Configuration Information]
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