# The Runtime

`CosIdRuntime` is the one registry and the one lifecycle owner. Spring Boot, cosid-server, the migration tool and
hand-written code all start a runtime the same way: a `RuntimeSpec` (configuration) and `RuntimeOptions` (everything
that is not configuration).

```java
RuntimeSpec spec = RuntimeSpec.builder("order-service")       // the namespace
    .store("db", StoreSpec.builder("jdbc").build())
    .machine(MachineSpec.Leased.builder("db").build())         // the default machine
    .machine("global", MachineSpec.Leased.builder("db")        // a named machine
        .namespace("global-ids").slotOffset(1024).build())
    .generator("default", GeneratorSpec.Snowflake.builder().build())
    .generator("token", GeneratorSpec.CosId80.builder().machine("global").build())
    .generator("order", GeneratorSpec.Segment.builder("db").build())
    .build();

public final class CosIdRuntime implements AutoCloseable {
    public static CosIdRuntime start(RuntimeSpec spec, RuntimeOptions options);
    public IdGenerator generator(String name);              // snowflake, segment, custom
    public StringIdGenerator stringGenerator(String name);  // long ids with their codec only
    public Supplier<String> text(String name);              // text ids of any strategy, cosid80 and uuidv7 included
    public PeriodicSegmentGenerator periodic(String name);
    public <T> T get(String name, Class<T> type);           // TimeBasedGenerator, SegmentGenerator, UuidV7Generator, PartsCodec, GroupedCodec, IntIdGenerator, ...
    public OptionalLong generated(String name);             // with countGenerated
    public Map<String, GeneratorInfo> describe();
    public Optional<SlotLease> slot();                      // the default machine's
    public Optional<SlotLease> slot(String machine);
    public Set<String> machines();                          // the started machines
    public IdFiller filler();
    public void close();
}
```

::: tip Text ids: `text(name)`
`text(name)` gives the text ids of **every** strategy: `long` ids through their codec, the 80-bit CosId, a periodic
segment, a UUIDv7. `stringGenerator(name)` is only the `long` generator with its codec: on cosid80, periodic-segment or
uuidv7 it fails with "issues no long ids; use text(name) for its text".
:::

## Specs

Each spec is built with `builder(<required>)`; the rest has defaults.

| Spec | Required | Optional (default) |
|---|---|---|
| `StoreSpec`: a store; `type` picks the `StoreProvider` | `type` | `timeout` (2s), `option(k, v)` |
| `MachineSpec.Leased`: a slot leased from a store | `store` | `bits` (10), `ttl` (30s), `tolerance` (1s), `acquireTimeout` (the ttl), `slotOffset` (0), `preferredSlot`, `ownerKey`, `namespace` (the runtime's) |
| `MachineSpec.Static`: a slot the deployment keeps unique | `slot` | `bits` (10), `stateFile`, `ahead` (10s), `notBefore`, `namespace` |
| `GeneratorSpec.Snowflake`: a time-based `long` generator | | `layout` (`SNOWFLAKE_MS`), `regression`, `codec`, `machine` (`default`) |
| `GeneratorSpec.CosId80`: the 80-bit CosId, text through a `PartsCodecSpec` | | `layout` (`COSID_80`), `regression`, `codec` (radix 62), `machine` |
| `GeneratorSpec.Segment`: a segment | `store` | `batch`, `codec` |
| `GeneratorSpec.PeriodicSegment`: a periodic segment | `store`, `period`, `zone` | `batch`, `periodic`, `codec` |
| `GeneratorSpec.Custom`: a `long` generator of your own, by name | `reference` | `codec` |
| `GeneratorSpec.Instance`: a `long` generator of your own, as an instance | `ids` | `codec` |
| `GeneratorSpec.Disabled(spec)`: declared but not created; calls throw `GeneratorDisabledException` (D24) | | |

`Custom` and `Instance` cover 4.x `IdGeneratorProvider.set(name, generator)`. `RuntimeOptions.generators` resolves a
`Custom` reference (in Spring, a bean name; a missing one fails the start). The runtime does not own such a generator:
it neither closes nor renews it, but serves it like any other (`generator`, `text`, `IdFiller`, `describe()` with
strategy `custom`, `countGenerated`, `Disabled`). A layout-based codec (`friendly`) is refused on it.

The machines: `machine(spec)` is the default one; `machine(name, spec)` adds a [named machine](slot-lease.md#named-machines),
which a time-based generator picks with `machine(name)`. Only the machines an enabled generator picks start.

The generator named **`default`** is 4.x's *share*: the primary bean in Spring, and the one cosid-server's `/ids`
uses without a name.

## Start and close

1. Validate everything and report all problems at once, one per line. Examples: an unknown store, `nodeBits <
   machine.bits`, a store timeout above a segment's `reserveTimeout`, a time-based generator without `machine`, a
   generator name with `/`.
2. Open the stores that enabled generators or the machine use, and only those.
3. Acquire the slot, if there is a time-based generator.
4. Create the generators.

A failure in any step closes what was opened. `close()` runs in reverse: the generators stop (each reports its exact
last timestamp), the lease is released with it, then the stores and the executor the runtime created are closed.

The set of generators is fixed at start-up (D17). There is no global singleton: a JVM may run several runtimes, each
with its own incarnation, and none sees the others. To add, remove or change generators programmatically, edit the spec
before `start` (in Spring: a `RuntimeSpecCustomizer` bean).

## Stores

```java
public interface StoreProvider {
    String type();                       // "jdbc", "redis", "mongo", "zookeeper", "remote" or your own
    Store open(StoreConfig config);
}
public interface Store extends AutoCloseable {
    RangeStore ranges();
    SlotLeaseStore slots();
}
public final class StoreConfig { String name(); String type(); Duration timeout(); Map<String, Object> options(); Optional<Object> client(); }
```

Providers are found by `ServiceLoader`. `RuntimeOptions.storeProviders` (in Spring, `StoreProvider` beans) add more and
win for the same type. `client` is an existing connection object: a `DataSource`, a Lettuce client or connection, a
`MongoClient`, a `CuratorFramework`. Without it, the provider builds one from `options` and closes it. `timeout` is
implemented natively by each store (JDBC query timeout, Redis command timeout, Mongo `timeoutMS`, Curator, HTTP).

A custom backend implements the two primitives and passes the [store TCK](../backends/test-suite.md).

## RuntimeOptions

| Option | Default |
|---|---|
| `time` | `TimeSource.SYSTEM` |
| `executor` | empty: the runtime creates a virtual-thread executor and closes it |
| `listener` | `GeneratorListener.NOOP`: the events of [metrics](../operations/monitoring.md) |
| `storeProviders` | none beyond `ServiceLoader` |
| `clients` | existing client objects by store name |
| `hostResolver`, `port` | the owner key: `host:port`, or `host:pid` without a port |
| `codecs` | resolves `custom` codecs; by class name by default |
| `generators` | resolves `custom` generators (`GeneratorSpec.Custom`); none by default |
| `countGenerated` | off: count the ids of every view (`generated(name)`), at a small cost per id |

## Compatibility of the Java types

From RC1 on, the public types change only by addition (api-freeze D39/D40):

- **Specs, options and SPI values are final classes with builders** (`RuntimeSpec`, `StoreSpec`, `MachineSpec.*`,
  `GeneratorSpec.*` except `Disabled`, `BatchPolicy`, `PeriodicPolicy`, `RuntimeOptions`, `StoreConfig`, `SlotLease`,
  `AcquireResult.Acquired`, `GeneratorInfo`). Build them with `builder(...)`, change one with `toBuilder()` or `with*`.
  They have no public constructor. A later minor version adds fields to the builder only, with a default equal to the
  behavior before, and never adds a required parameter to `builder(...)`.
- **Closed values stay records**: `Range`, `IdParts`, `GroupedId`, `Owner`, `Layout`, `ClockRegressionPolicy`, the
  codec specs (`CodecSpec.*`, `PartsCodecSpec.*`, `GroupedCodecSpec.*`), `AcquireResult.Exhausted` and
  `GeneratorSpec.Disabled`. Their constructors and record patterns are part of the promise.
- **Sealed specs may gain variants in a minor version**: `GeneratorSpec`, `MachineSpec`, `CodecSpec`,
  `PartsCodecSpec`, `GroupedCodecSpec`. A `switch` over one keeps a `default` branch. `AcquireResult` is closed.
- Not covered: the `internal` packages and the Spring binding class `CosIdProperties` (the `cosid.*` keys are the
  promise, not the Java type).

## Owner key

The owner key of a leased slot is, in order: `machine.leased.owner-key` if set; `host:port` when a port is known (the
web server port in Spring); otherwise `host:pid`. Spring Cloud's `InetUtils` gives the host when present. The key only
drives the sticky slot choice and observation; uniqueness does not depend on it.

## describe()

`describe()` replaces the 4.x Stat classes: per generator its strategy, store and details (layout, slot, window, batch
size, ids held, period, ...). The Actuator `cosid` endpoint serves it.
