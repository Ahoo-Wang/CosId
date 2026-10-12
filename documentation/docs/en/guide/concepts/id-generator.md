# ID Generators

All CosId generators implement one of two interfaces in `cosid-core`:

```java
public interface StringIdGenerator extends Statistical {
    String generateAsString();
}

public interface IdGenerator extends StringIdGenerator {
    long generate();

    default IdConverter idConverter() {
        return ToStringIdConverter.INSTANCE;
    }

    @Override
    default String generateAsString() {
        return idConverter().asString(generate());
    }
}
```

An `IdGenerator` produces `long` IDs and formats them as strings through its [ID converter](./id-converter.md). A `StringIdGenerator` produces strings only: `CosIdGenerator` and `UuidGenerator` are string-only generators and have no `generate()`.

<p align="center">
  <img src="../../../public/assets/design/IdGenerator-impl-class.png" alt="IdGenerator implementation class diagram"/>
</p>

## The generators

| Generator | Interface | Coordination | Notes |
|-----------|-----------|--------------|-------|
| [`SegmentChainId`](./segment.md) | `IdGenerator` (`SegmentId`) | Takes segments from an `IdSegmentDistributor` and prefetches ahead | The default segment mode (`cosid.segment.mode=chain`) |
| [`DefaultSegmentId`](./segment.md) | `IdGenerator` (`SegmentId`) | Takes a segment when the current one runs out, on the calling thread | `cosid.segment.mode=segment` |
| [`MillisecondSnowflakeId`](./snowflake.md) | `IdGenerator` (`SnowflakeId`) | Needs a [machine id](./machine-id.md) | The default Snowflake layout: 41-bit timestamp, 10-bit machine id, 12-bit sequence |
| [`SecondSnowflakeId`](./snowflake.md) | `IdGenerator` (`SnowflakeId`) | Needs a machine id | Timestamp in seconds, `timestamp-unit: second` |
| [`CosIdGenerator`](./cosid-generator.md) | `StringIdGenerator` | Needs a machine id | Radix-62, radix-36 or friendly strings; up to 20 machine id bits |
| `UuidGenerator` | `StringIdGenerator` | None | `UuidGenerator.INSTANCE`, random UUID strings, `UUID.randomUUID().toString()` |

Decorators add behavior to a generator without changing its type: `ClockSyncSnowflakeId` waits out a clock that moved backwards, `LeasedSnowflakeId` stops generating once the machine id lease is lost, and `StringSnowflakeId` and `StringSegmentId` use a configured converter for `generateAsString()`. The Spring Boot starter applies them from configuration.

## Choosing a generator

1. **Do you need a `long`?** Most databases and ORMs want a numeric primary key. Use a segment chain or a Snowflake ID. If strings are fine, every generator can produce them.
2. **Do you want IDs close together?** A segment chain hands out near-sequential numbers (`1, 2, 3, ...` per instance, in blocks of `step`), which keeps keys short and indexes dense. Snowflake IDs are large numbers (around 2<sup>60</sup>).
3. **Must the ID carry its time?** A Snowflake ID can be parsed back into its timestamp, machine id and sequence, which also lets you shard by time with the ID alone (see [interval sharding](../integrations/sharding/interval-timeline.md)).
4. **What do you already run?** A segment chain needs a segment store that is reachable when it prefetches; Snowflake and CosIdGenerator need a store only to lease a machine id at startup and to renew it.
5. **How many instances?** The default Snowflake layout has 10 machine id bits, 1024 instances per namespace. `CosIdGenerator` has 20, about a million.
6. **Is the ID shown to a browser?** JavaScript numbers are exact only up to 2<sup>53</sup>. Send Snowflake IDs as strings, or use a [JavaScript-safe layout](./snowflake.md#javascript-safe-ids).

When in doubt, start with a segment chain for primary keys and business numbers.

## Using a generator

With the Spring Boot starter, every configured generator is registered in the `IdGeneratorProvider` under its name, and the share generator under `__share__`:

```java
@Service
public class OrderService {
    private final IdGeneratorProvider provider;

    public OrderService(IdGeneratorProvider provider) {
        this.provider = provider;
    }

    public long newOrderId() {
        return provider.getRequired("order", IdGenerator.class).generate();
    }

    public long newShareId() {
        return provider.getShare().generate();
    }
}
```

Each generator is also a bean named `{name}SegmentId` or `{name}SnowflakeId` (for example `orderSegmentId`, `__share__SnowflakeId`), and the CosId generator is the `CosIdGenerator` bean, registered in the provider as `cosid`. See [ID Generator Provider](./provider.md) for the lookups, and [`@CosId`](../integrations/annotation.md) to fill entity fields without calling a generator yourself.
