# CosIdGenerator

`CosIdGenerator` generates time-ordered string IDs for very large clusters. Its 80-bit layout leaves 20 bits for the machine id, about a million instances in one namespace, where a 63-bit Snowflake ID has room for 1024.

<p align="center">
  <img src="../../../public/assets/design/CosIdGenerator.png" alt="CosIdGenerator design diagram"/>
</p>

## Layout

`timestamp (44 bits) + machine id (20 bits) + sequence (16 bits) = 80 bits`

- **Timestamp**: milliseconds since the Unix epoch.
- **Machine id**: leased like a Snowflake machine id, see [Machine ID](./machine-id.md).
- **Sequence**: 65,536 IDs per millisecond per instance, restarting past half its range like the [Snowflake sequence](./snowflake.md#sequence-reset).

The ID is the three parts encoded as fixed-width strings, so IDs sort as strings in the order they were generated, and the parts can be parsed back (`getStateParser().asState(id)`).

| Type | Encoding | Length | Characters per part |
|------|----------|--------|----------------|
| `RADIX62` (default) | `0-9A-Za-z` | 15 characters | timestamp 8 + machine id 4 + sequence 3 |
| `RADIX36` | `0-9A-Z` | 17 characters | timestamp 9 + machine id 4 + sequence 4 |
| `FRIENDLY` | `yyyyMMddHHmmssSSS-machineId-sequence` | variable | readable, in `cosid.generator.zone-id` |

Radix-62 IDs are case sensitive: store them in a case-sensitive (binary) column.

## Properties

- Time-ordered: increasing per instance, trend increasing across instances.
- Fast: about 15.6 million IDs per second on one instance, three times `UUID.randomUUID()`.
- Short: 15 characters, against 36 for a UUID string.

`CosIdGenerator` is a `StringIdGenerator`: it has no `long` form, so it cannot fill a `long` field or serve as the share generator.

## Configuration

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis
  generator:
    enabled: true
```

The starter registers a `CosIdGenerator` bean, which is also registered in the `IdGeneratorProvider` as `cosid`. Annotate a `String` field with `@CosId("cosid")` to fill it with this generator. See the [CosIdGenerator configuration](../../reference/config/cosid-generator.md).
