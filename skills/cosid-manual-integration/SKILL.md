---
name: cosid-manual-integration
description: Integrate CosId programmatically into Java or Kotlin applications without Spring Boot auto-configuration. Use for direct construction of SnowflakeId, CosIdGenerator, DefaultSegmentId, or SegmentChainId; machine-ID allocation and guarding; backend distributor factories; lifecycle ownership; custom IdConverter wiring; tests; or integration into another framework. Do not use for cosid-spring-boot-starter YAML or feature capabilities.
---

# Integrate CosId Programmatically

Provide code that compiles against the user's CosId version. Prefer direct constructors for fixed local inputs and backend factories for distributed state.

## Workflow

1. Confirm the target version and chosen strategy. Use `$cosid-strategy-guide` only when the strategy is undecided.
2. Identify the uniqueness authority: fixed machine ID, StatefulSet ordinal, distributed machine-ID backend, or segment backend.
3. Construct the smallest generator that satisfies the requirement.
4. Own every created client, guarder, executor, and distributor for its full lifecycle.
5. Add one focused check for uniqueness, local monotonicity, parsing, restart, or clock rollback.

When a CosId checkout is available, verify signatures in `cosid-core/src/main/java/me/ahoo/cosid/` and use existing backend factories and tests as examples. Do not copy older README snippets without checking the current source.

## SnowflakeId with a Fixed Machine ID

Use this only when deployment automation guarantees a unique value per live instance.

```java
int machineId = 1;
SnowflakeId idGenerator = new ClockSyncSnowflakeId(
    new MillisecondSnowflakeId(machineId)
);

long id = idGenerator.generate();
```

Use `SecondSnowflakeId` only for a deliberate second-based bit layout. Use `SafeJavaScriptSnowflakeId.ofMillisecond(machineId)` when a numeric ID must stay within JavaScript's safe integer range; otherwise serialize ordinary IDs as strings.

## Distributed Machine-ID Allocation

Create the backend-specific `MachineIdDistributor`, then allocate with the exact namespace, machine bits, instance identity, and lease:

```java
static SnowflakeId createSnowflake(MachineIdDistributor distributor) {
    String namespace = "order-service";
    InstanceId instanceId = InstanceId.of("10.0.0.12:8080", false);
    Duration lease = Duration.ofMinutes(5);

    MachineState state = distributor.distribute(
        namespace,
        10,
        instanceId,
        lease
    );
    return new ClockSyncSnowflakeId(
        new MillisecondSnowflakeId(state.getMachineId())
    );
}
```

The snippet shows allocation, not heartbeat ownership. For expiring distributed leases, wrap allocation with `GuardDistribute`, run a `DefaultMachineIdGuarder`, stop it during shutdown, and call `distributor.revert(namespace, instanceId)` after stopping the guarder. Gate readiness and generation on `guarder.hasFailure()` because the guarder records heartbeat failure but does not stop ID generation. Shut down any `ScheduledExecutorService` created by the application after stopping the guarder. Pass `stable: true` only for a stable deployment identity such as a StatefulSet slot; stable instances retain their machine-ID claim instead of being recycled by the lease cutoff. Use local state storage only when the filesystem follows that stable identity; it helps reclaim the previous machine ID and prevents clock regression after restart.

For a fixed manual ID through the distributor API, use the real constructor:

```java
MachineIdDistributor distributor = new ManualMachineIdDistributor(
    1,
    MachineStateStorage.LOCAL,
    ClockBackwardsSynchronizer.DEFAULT
);
```

## Segment Generators

Obtain the distributor from the selected backend's `IdSegmentDistributorFactory` and keep the namespace/name/offset/step definition explicit:

```java
static SegmentId createSegment(IdSegmentDistributorFactory distributorFactory) {
    IdSegmentDistributorDefinition definition =
        new IdSegmentDistributorDefinition("order-service", "order", 0, 100);
    IdSegmentDistributor distributor = distributorFactory.create(definition);
    return new SegmentChainId(distributor);
}
```

Use `new DefaultSegmentId(distributor)` instead when synchronous rollover is preferred. `SegmentChainId` uses `PrefetchWorkerExecutorService.DEFAULT`, which installs a JVM shutdown hook; if the application supplies its own executor, shut that executor down explicitly.

Segment IDs are monotonic only within one generator. Distributed ranges provide global uniqueness and trend ordering, not strict global generation-time order.

## String Conversion

Wrap `long` generators with the matching string decorator instead of reimplementing generation:

```java
IdConverter converter = new PrefixIdConverter(
    "ORD-",
    Radix62IdConverter.PAD_START
);
SnowflakeId stringSnowflake = new StringSnowflakeId(snowflakeId, converter);
SegmentId stringSegment = new StringSegmentId(segmentId, converter);
```

Call `generateAsString()` on the wrapper and test `converter.asLong()` when round-trip parsing is required. `CosIdGenerator` does not support `IdConverter`; select or implement its `CosIdIdStateParser` instead.

## CosIdGenerator

`CosIdGenerator` produces strings; calling `generate()` throws `UnsupportedOperationException`.

```java
CosIdGenerator generator = new ClockSyncCosIdGenerator(
    new Radix62CosIdGenerator(machineId)
);
String id = generator.generateAsString();
```

Allocate `machineId` with the same uniqueness and lifecycle rules as SnowflakeId.

## Boundaries and Checks

- Keep one namespace per independent allocation domain and one stable instance identity per deployment slot.
- Reject machine IDs outside the configured bit range before starting traffic.
- Do not claim global monotonicity for segment generators or global total order for clock-based generators.
- Keep custom epoch, timestamp unit, bit layout, parser, and sharding converter aligned.
- Close backend clients created by the application. Do not close shared framework clients that the application does not own.
- Test with at least two simulated instances when correctness depends on distributed uniqueness.

## Response Contract

Return the required module dependencies, exact imports and constructors, lifecycle ownership, one runnable check, and no unrelated Spring Boot configuration.
