# Production Checklist

## Generators

- **Pick the generator from the requirement**, see [ID Generators](../concepts/id-generator.md#choosing-a-generator). A segment chain is the default choice for numeric keys.
- **Set `cosid.namespace`** to the application name. Applications that share a store must not share a namespace unless they share the IDs.
- **Keep one share generator.** With both `snowflake` and `segment` enabled, turn one `share` off, and use named generators for anything that matters.
- **Never change a Snowflake layout in place**: `epoch`, `timestamp-bit`, `machine-bit` and `sequence-bit` of a generator stay fixed for the lifetime of its IDs.
- **Send Snowflake IDs to browsers as strings**, or use a 53-bit layout, see [JavaScript-safe IDs](../concepts/snowflake.md#javascript-safe-ids).
- **Test converter output** against the column it goes into: length, padding, prefix and, for radix-62, case sensitivity.

## Segments

- **Choose `step` for ordering and gaps, not throughput**: a segment chain grows its prefetch under load, so a small step (10 to 100) is usually enough. Every restart skips the rest of the instance's segments.
- **Raise `chain.safe-distance`** only if the logs show frequent `Hunger` expansions.
- **Use a store that persists every write.** A counter that goes back hands out IDs again.
- **Set `offset`** above the highest existing ID when a table already has rows.
- **Bound the proxy's `segment.max-step`** at or above `step × max-prefetch-distance` of every client.

## Machine ids

- **Lease machine ids from a store**; use `manual` only for a single instance.
- **Use the same `cosid.machine.guarder.safe-guard-duration`** on every instance of a namespace.
- **Keep `cosid.machine.guarder.fail-fast` on** (the default): a failed request is better than a duplicate ID.
- **Size `machine-bit`** for the largest number of instances during a rollout, plus those whose leases have not expired yet.
- **Mark only instances with a fixed identity as `stable`**, together with a fixed `instance-id`.
- **Keep host clocks in sync** with each other and with the store's clock (NTP, slewing rather than stepping). Required for ZooKeeper leases, and it keeps restarts from waiting on a watermark.

## Operations

- **Alert on the `machineId` health indicator**, see [Monitoring](./monitoring.md).
- **Expose the actuator endpoints only to trusted callers**; `cosidGenerator` generates IDs, and `cosid.actuator.delete.enabled` stays off.
- **Run the proxy server on an internal network with tokens**, see [Proxy deployment security](../backends/proxy.md#deployment-security).
- **Give the JDBC backend a dedicated `DataSource`**, outside business transactions.
- **Follow [Rolling Upgrade](./rolling-upgrade.md)** for restarts and version upgrades.
