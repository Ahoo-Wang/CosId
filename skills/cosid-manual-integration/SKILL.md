---
name: cosid-manual-integration
description: Integrate CosId 5 programmatically into Java or Kotlin applications without Spring Boot. Use for building a RuntimeSpec and starting a CosIdRuntime, StoreSpec / MachineSpec / GeneratorSpec / CodecSpec records, RuntimeOptions (clients, listeners, executor, time source), direct TimeBasedGenerator or SegmentGenerator construction in tests, Layout and IdCodec use, IdFiller, lifecycle and close order, custom StoreProvider or JdbcDialect, and tests with cosid-store-tck's InMemoryStore. Do not use for cosid-spring-boot-starter YAML.
---

# Integrate CosId Programmatically

Provide code that compiles against the user's CosId version. In CosId 5 **`CosIdRuntime` is the one assembly path**:
Spring, cosid-server and hand-written code all call `CosIdRuntime.start(spec, options)`. Build a spec; do not wire
generators, leases and stores by hand in production code.

## Workflow

1. Confirm the target version (5.x needs Java 21) and the chosen strategy. Use `$cosid-strategy-guide` when the strategy
   is undecided.
2. Identify the uniqueness authority: a leased slot from a store, a static slot the deployment guarantees, or a
   segment store.
3. Build the smallest `RuntimeSpec` that satisfies the requirement.
4. Own the runtime for its full life (`try`-with-resources, or close it on shutdown).
5. Add one focused check: uniqueness, monotonicity, decoding, restart, or clock regression.

In a CosId checkout, verify signatures in `core/src/main/java/me/ahoo/cosid/runtime/` (records) and use
`core/src/test/java/me/ahoo/cosid/runtime/CosIdRuntimeTest.java` as the example.

## A Runtime

```java
RuntimeSpec spec = new RuntimeSpec("shop",
    Map.of("db", new StoreSpec("jdbc", Duration.ofSeconds(2), Map.of("initSchema", "true"))),
    Optional.of(new MachineSpec.Leased("db", 10, Duration.ofSeconds(30), Duration.ofSeconds(1),
        OptionalInt.empty(), Optional.empty())),                  // preferred slot, owner key
    Map.of(
        "default", new GeneratorSpec.Snowflake(Layout.SNOWFLAKE_MS, ClockRegressionPolicy.DEFAULT,
            List.of(new CodecSpec.Radix(62, 11, true))),
        "order", new GeneratorSpec.Segment("db", BatchPolicy.DEFAULT, List.of(new CodecSpec.Prefix("O")))));

RuntimeOptions d = RuntimeOptions.defaults();
RuntimeOptions options = new RuntimeOptions(d.time(), d.executor(), d.listener(), d.storeProviders(),
    Map.of("db", dataSource), d.hostResolver(), d.port(), d.codecs());   // existing clients by store name

try (CosIdRuntime runtime = CosIdRuntime.start(spec, options)) {
    long id = runtime.generator("default").generate();
    String order = runtime.stringGenerator("order").generate();
}
```

- Store types: `jdbc` (`DataSource`, or `options.url`), `redis` (Lettuce client or `options.uri`), `mongo`
  (`MongoClient` or `options.uri`), `zookeeper` (`CuratorFramework` or `options.connectString`), `remote`
  (`options.baseUrl`, `token`). Providers come from `ServiceLoader`; `RuntimeOptions.storeProviders` adds more.
- A store given a client never closes it; one it built itself, it closes.
- `MachineSpec.Static(bits, slot, Optional.of(stateFile), Duration.ofSeconds(10), Optional.empty())` for a slot the
  deployment keeps unique; without a state file there is no protection against a clock set back across restarts.
- `GeneratorSpec.CosId80(regression, new PartsCodecSpec.Radix(62))`,
  `GeneratorSpec.PeriodicSegment(store, Period.YEAR_MONTH_DAY, zone, BatchPolicy.DEFAULT, PeriodicPolicy.DEFAULT,
  new GroupedCodecSpec.DatePrefix("-", List.of(new CodecSpec.Radix(10, 6, true))))`.
- `GeneratorSpec.Disabled(spec)` declares a generator without creating it.
- A segment's `BatchPolicy(step, initialOffset, lookahead, maxBatch, reserveTimeout, rangeTtl)`: use an initial offset of
  1 for entity ids (0 counts as unset for `IdFiller`).

## Reading Ids

- `runtime.generator(name)` returns an `IdGenerator` (`snowflake`, `segment`); `runtime.stringGenerator(name)` returns
  ids and codec.
- `runtime.get(name, TimeBasedGenerator.class)` gives `layout()`, `slot()`, `nextParts()`;
  `runtime.get(name, PartsCodec.class)` for `cosid80`; `runtime.periodic(name)` and `runtime.get(name, GroupedCodec.class)`
  for periodic segments; `runtime.get(name, IntIdGenerator.class)` is range checked.
- `layout.unpack(id)` gives `IdParts(time, node, seq)`; `parts.instant(layout)` the time. Every codec has `decode`.
- `runtime.filler().fill(entity)` fills `@CosId` fields.
- `IdGenerators.toInt`, `.uncertain`, `.local()`, `.uuid()`, `.lazy(...)` replace the small 4.x generator classes.

## Lifecycle

- Start validates everything first and reports all problems in one `CosIdException`. It then opens the used stores,
  acquires the slot, and creates the generators; a failure closes what was opened.
- `close()`: generators stop and report their exact last timestamp, the slot is released with it, then the stores and the
  runtime's own executor close. Always close: a released slot is reusable at once, an abandoned one after `ttl` +
  `tolerance`.
- A JVM may run several runtimes; each has its own incarnation and slot. There is no global state.

## Tests

- `cosid-store-tck`: `InMemoryStore` (passes every contract) for tests without a database, `ManualClock`, fault
  injection, and the `RangeStoreContract` / `SlotLeaseStoreContract` base classes for a custom store.
- A custom `JdbcDialect` is registered through `META-INF/services/me.ahoo.cosid.store.jdbc.JdbcDialect` and must pass the
  contracts on its database.

## Boundaries and Checks

- Do not construct `LeaseKeeper` or `TimeBasedGenerator` directly in application code; it bypasses the start-up checks
  (node bits vs slot bits, store timeout vs reserve timeout).
- Uniqueness of time-based ids rests on the store's window invariant (D22), never on clocks; there is no way to keep
  issuing after a lost lease.
- 4.x classes (`SnowflakeId`, `SegmentChainId`, `MachineIdDistributor`, `IdGeneratorProvider`, `IdConverter`) do not exist
  in 5.x.

## Response Contract

Return:

1. the dependencies (`cosid-core` + the store artifacts);
2. the `RuntimeSpec` and `RuntimeOptions`;
3. the lifecycle owner and close point;
4. one focused test.
