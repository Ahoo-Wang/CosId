# Basic Configuration

> `me.ahoo.cosid.spring.boot.starter.CosIdProperties`, prefix `cosid`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `cosid.enabled` | `boolean` | `true` | Turns the whole starter off when `false` |
| `cosid.namespace` | `String` | `cosid` | Isolates the counters and machine ids of one application from others in the same store. Set it to the application name: `${spring.application.name}` |
| `cosid.provider.isolated` | `boolean` | `false` | `false`: the `IdGeneratorProvider` bean is `DefaultIdGeneratorProvider.INSTANCE`. `true`: each `ApplicationContext` gets its own provider, cleared when the context closes, so generators are not shared between contexts (devtools restarts, cached test contexts); `INSTANCE` then no longer holds Spring-registered generators |
| `cosid.metrics.enabled` | `boolean` | `true` | Registers the CosId Micrometer meters when Micrometer is on the classpath, see [Monitoring](../../guide/operations/monitoring.md#metrics) |
| `cosid.metrics.generated.enabled` | `boolean` | `false` | Counts the IDs each segment and Snowflake generator generates (`cosid.generator.generated`), by wrapping them in a counting decorator |
| `cosid.max-concurrent-calls` | `int` | `64` | Most store calls with a timeout (ZooKeeper, Redis) running at once in the JVM, across all stores. Beyond it calls fail fast with `CallRejectedException` instead of piling up threads behind a hung store |

The generators have their own pages: [machine ID](./machine.md) (`cosid.machine`), [SnowflakeId](./snowflake.md) (`cosid.snowflake`), [SegmentId](./segment.md) (`cosid.segment`) and [CosIdGenerator](./cosid-generator.md) (`cosid.generator`). Backend properties are on the [ZooKeeper](./zookeeper.md) and [proxy](./proxy.md) pages and the backend guides.

```yaml
cosid:
  namespace: ${spring.application.name}
```

## ID converter

> `me.ahoo.cosid.spring.boot.starter.IdConverterDefinition`

Every Snowflake and segment generator has a `converter`, which formats `generateAsString()`. See [ID converters](../../guide/concepts/id-converter.md) for the output of each.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `type` | `Type` | `radix` | `to_string`, `radix`, `radix36`, `snowflake_friendly` (Snowflake IDs only) or `custom` |
| `prefix` | `String` | none | Text before the ID |
| `suffix` | `String` | none | Text after the ID |
| `radix.char-size` | `int` | `11` | Length of a radix-62 string, at most 11 |
| `radix.pad-start` | `boolean` | `true` | Pad with `0` to `char-size`, so strings sort like the numbers |
| `radix36.char-size` | `int` | `13` | Length of a radix-36 string, at most 13 |
| `radix36.pad-start` | `boolean` | `true` | Pad with `0` to `char-size` |
| `to-string.char-size` | `int` | `11` | Length when padding |
| `to-string.pad-start` | `boolean` | `false` | Pad with `0` to `char-size` |
| `friendly.pad-start` | `boolean` | `false` | `snowflake_friendly`: pad the machine id and sequence with `0`, so strings sort like the IDs |
| `date-prefix.enabled` | `boolean` | `false` | Prefix the date of generation |
| `date-prefix.pattern` | `String` | `yyMMdd` | Date pattern |
| `date-prefix.delimiter` | `String` | `-` | Between the date and the ID |
| `date-prefix.before-prefix` | `boolean` | `true` | Put the date inside (`true`) or outside `prefix` |
| `group-prefix.enabled` | `boolean` | `false` | Prefix the group of a grouped segment ID; needs `group.by` |
| `group-prefix.delimiter` | `String` | `-` | Between the group and the ID |
| `group-prefix.before-prefix` | `boolean` | `true` | Put the group inside (`true`) or outside `prefix` |
| `custom.type` | `Class<? extends IdConverter>` | none | `custom`: the converter class, with a public no-argument constructor |

```yaml
cosid:
  segment:
    share:
      converter:
        type: radix
        prefix: cosid_
        radix:
          char-size: 8
          pad-start: true
```

## Integration switches

Each optional integration applies when its module is on the classpath, and can be turned off:

| Property | Default | Description |
|----------|---------|-------------|
| `cosid.mybatis.enabled` | `true` | MyBatis plugin that fills id fields |
| `cosid.jdbc.enabled` | `false` (`true` in 3.x) | Spring Data JDBC integration: the `@Id` field parser and the before-convert callback. The `@Id` parser joins the global field parser, so it also applies on other paths such as MyBatis. While it is off, an INFO at startup names the Spring Data JDBC entities with an `@Id` property or `@CosId` fields, which CosId does not fill |
| `cosid.flowable.enabled` | `true` | Flowable id generator |
| `cosid.activiti.enabled` | `true` | Activiti id generator |
| `cosid.activiti.generator-name`, `cosid.flowable.generator-name`, `cosid.axon.generator-name` | the `cosid.activiti`, `cosid.flowable`, `cosid.axon` system property, else the shared generator | The id generator of each integration |
| `cosid.actuator.delete.enabled` | `false` (`true` in 3.x) | The `DELETE` operation of the `cosid` actuator endpoint, which removes an id generator at runtime |

## Actuator endpoints

With Spring Boot Actuator on the classpath, the starter adds three endpoints, created only when they are available (enabled and exposed through `management.endpoints.web.exposure.include`):

| Endpoint | Operations |
|----------|------------|
| `cosid` | `GET /actuator/cosid`: every generator and its state; `GET /actuator/cosid/{name}`: one generator; `DELETE /actuator/cosid/{name}`: remove one, with `cosid.actuator.delete.enabled` |
| `cosidGenerator` | `GET /actuator/cosidGenerator`: an ID of the share generator; `GET /actuator/cosidGenerator/{name}`: an ID of a named `long` generator |
| `cosidStringGenerator` | `GET /actuator/cosidStringGenerator`, `GET /actuator/cosidStringGenerator/{name}`: the same, as strings |

The `machineId` health indicator reports `DOWN` while a machine id lease could not be renewed.
