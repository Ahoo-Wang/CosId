# Store TCK

`cosid-store-tck` holds the contracts every store passes, an in-memory store, and test tools. Use it to test a custom
`StoreProvider`, a new `JdbcDialect`, or your application without a database.

```kotlin
testImplementation("me.ahoo.cosid:cosid-store-tck")
```

```java
class MyRangeStoreTest extends RangeStoreContract {
    @Override
    protected RangeStore newStore() { return new MyStore(...).ranges(); }
}
class MySlotLeaseStoreTest extends SlotLeaseStoreContract {
    @Override
    protected SlotLeaseStore newStore() { return new MyStore(...).slots(); }
}
```

| Area | Cases |
|---|---|
| ranges | the first range starts at `initialOffset`; one of concurrent first creations wins; N threads × M store instances never overlap and increase strictly; values above 2^53 exact; `RangeExhaustedException` at the end; sealed keys fail and keep their value; one connection reserving different keys (no stale `last_insert_id`) |
| lease basics | the slot choice order (idempotent, preferred, sticky, lowest); `Exhausted`; the `limit` boundary |
| safety | after a takeover `floor ≥` the previous final `ceiling`; after a release `floor ≥ lastIssued`; `ceiling` and `next_floor` monotonic; the same key with another incarnation is never taken over; an expired holder's renew and release fail; renewals are idempotent |
| clock | `storeNow` comes from the store; `expiresAt − storeNow == ttl` from the same write |
| concurrency | N clients acquiring in one namespace get distinct slots |
| metadata | other namespace parameters are refused |

Also in the module:

- `InMemoryStore` passes every contract; the core and Spring tests use it, and so can yours. It is not registered as a
  `StoreProvider`: create it directly, or return it from your own provider passed to `RuntimeOptions.storeProvider(...)`.
- `ManualClock` drives the store clock in tests.
- Fault injection wrappers add latency, timeouts, and lost responses that are replayed.
- `ContractsRan` reads JUnit XML reports and fails when contract cases were skipped. Every CosId backend runs it in
  `check`, so a CI job without its database cannot pass silently.

## Checking that the contracts ran

`ContractsRan` takes the directory of the JUnit XML reports (`TEST-*.xml`), then optionally the contract classes to
check (by default `RangeStoreContract` and `SlotLeaseStoreContract`). Run it on the test runtime classpath after the
tests. For every contract, some test class extending it must report every case as passed; a case marked
`@OptionalCapability` may be skipped. Otherwise it lists the missing cases and exits with status 1.

Gradle, as the CosId backends register it:

```kotlin
val checkContractsRan by tasks.registering(JavaExec::class) {
    group = "verification"
    val reports = layout.buildDirectory.dir("test-results/test")
    dependsOn(tasks.test)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.ahoo.cosid.store.tck.ContractsRan")
    argumentProviders.add(CommandLineArgumentProvider { listOf(reports.get().asFile.absolutePath) })
}
tasks.check {
    dependsOn(checkContractsRan)
}
```

Maven, with the Surefire reports and the `exec` goal (a separate JVM, so the exit status fails the build):

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.5.0</version>
    <executions>
        <execution>
            <id>check-contracts-ran</id>
            <phase>verify</phase>
            <goals>
                <goal>exec</goal>
            </goals>
            <configuration>
                <executable>java</executable>
                <classpathScope>test</classpathScope>
                <arguments>
                    <argument>-classpath</argument>
                    <classpath/>
                    <argument>me.ahoo.cosid.store.tck.ContractsRan</argument>
                    <argument>${project.build.directory}/surefire-reports</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```
