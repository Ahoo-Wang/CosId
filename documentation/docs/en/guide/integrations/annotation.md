# @CosId Field Filling

`@CosId` marks the id field of an entity; `IdFiller` fills it from a generator. The MyBatis and Spring Data JDBC
integrations call it before an insert, and you can call it yourself: `runtime.filler().fill(entity)` (in Spring, inject
`IdFiller`). It is part of `cosid-core` (`me.ahoo.cosid.accessor`).

```java
public class Order {
    @CosId                  // the "default" generator
    private long id;
}

public class Bill {
    @CosId("bill")          // a named generator
    private String id;      // text through the generator's codec
}

@CosId(value = "order", field = "orderId")   // on the type: names the field
public class LineItem extends BaseEntity { }
```

- Field types: `long` / `Long`, `int` / `Integer` (range checked, `IdOverflowException`), and `String` (through the
  generator's codec; `cosid80` through its `PartsCodec`, `periodic-segment` through its `GroupedCodec`).
- **A field is filled only when unset**: `≤ 0`, `null`, or an empty string.
- Getters and setters are used when present, the field otherwise. Superclasses are searched, and generic field types are
  resolved. A class has at most one id.
- The accessor of each class is resolved on first use and cached as method handles. There is no scanning or
  registration step (4.x's accessor registry and scanner are gone).

::: tip Segment ids start at `initial-offset`
A new segment key starts at `initial-offset` (default `0`), and `0` counts as "unset". Use `initial-offset: 1` for
entity ids.
:::
