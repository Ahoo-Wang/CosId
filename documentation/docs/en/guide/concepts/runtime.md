# The Runtime

`CosIdRuntime` is the one registry and the one lifecycle owner. Spring Boot, cosid-server, the migration tool and
hand-written code all start a runtime the same way: a `RuntimeSpec` (configuration) and `RuntimeOptions` (everything
that is not configuration).

```java
public record RuntimeSpec(String namespace,
                          Map<String, StoreSpec> stores,
                          Optional<MachineSpec> machine,
                          Map<String, GeneratorSpec> generators) {}

public final class CosIdRuntime implements AutoCloseable {
    public static CosIdRuntime start(RuntimeSpec spec, RuntimeOptions options);
    public IdGenerator generator(String name);              // snowflake, segment
    public StringIdGenerator stringGenerator(String name);  // with its codec
    public PeriodicSegmentGenerator periodic(String name);
    public <T> T get(String name, Class<T> type);           // TimeBasedGenerator, SegmentGenerator, PartsCodec, GroupedCodec, IntIdGenerator, ...
    public Map<String, GeneratorInfo> describe();
    public Optional<SlotLease> slot();
    public IdFiller filler();
    public void close();
}
```

## Specs

| Spec | Records |
|---|---|
| `StoreSpec(type, timeout, options)` | a store; `type` picks the `StoreProvider` |
| `MachineSpec.Leased(store, bits, ttl, tolerance, preferredSlot, ownerKey)` | a slot leased from a store |
| `MachineSpec.Static(bits, slot, stateFile, ahead, notBefore)` | a slot the deployment keeps unique |
| `GeneratorSpec.Snowflake(layout, regression, codec)` | a time-based `long` generator |
| `GeneratorSpec.CosId80(regression, codec)` | the 80-bit CosId, text through a `PartsCodecSpec` |
| `GeneratorSpec.Segment(store, batch, codec)` | a segment |
| `GeneratorSpec.PeriodicSegment(store, period, zone, batch, periodic, codec)` | a periodic segment |
| `GeneratorSpec.Disabled(spec)` | declared but not created; calls throw `GeneratorDisabledException` (D24) |

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
public record StoreConfig(String name, String type, Duration timeout, Map<String, Object> options, Optional<Object> client) {}
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
| `countGenerated` | off: count the ids of every view (`generated(name)`), at a small cost per id |

## Owner key

The owner key of a leased slot is, in order: `machine.leased.owner-key` if set; `host:port` when a port is known (the
web server port in Spring); otherwise `host:pid`. Spring Cloud's `InetUtils` gives the host when present. The key only
drives the sticky slot choice and observation; uniqueness does not depend on it.

## describe()

`describe()` replaces the 4.x Stat classes: per generator its strategy, store and details (layout, slot, window, batch
size, ids held, period, ...). The Actuator `cosid` endpoint serves it.
