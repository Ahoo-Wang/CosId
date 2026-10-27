# Spring Data JDBC

`CosIdBeforeConvertCallback` (artifact `cosid-spring-data-jdbc`, package `me.ahoo.cosid.spring.data.jdbc`) fills, before an aggregate is converted for saving, its
`@CosId` field, and then an unset `@Id` from a generator (`default` unless configured).

```java
public class Order {
    @Id
    private Long id;          // filled from cosid.jdbc.id-generator when unset
}
```

It is off by default, as in 4.x:

| Property | Default |
|---|---|
| `cosid.jdbc.enabled` | `false` |
| `cosid.jdbc.id-generator` | `default` |
