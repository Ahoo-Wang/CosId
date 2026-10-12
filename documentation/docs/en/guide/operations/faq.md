# FAQ

## Do I need to deploy a CosId server?

No. CosId is a library: IDs are generated inside your application, and the store you already run (Redis, a database, MongoDB or ZooKeeper) holds the counters and leases. A separate ID service would add a network call to every ID, which is what CosId avoids. The [CosId proxy](../backends/proxy.md) is optional; it stands in for the store when applications must not reach it, and IDs are still generated locally.

## Are the IDs sequential?

Within one instance, yes: each instance hands out increasing IDs. Across instances they are *trend increasing*: a segment ID from one instance can be lower than one issued earlier by another, by up to about `step` × the segments each instance holds (the current one and the prefetched ones: at least `safe-distance`, more under load) × the number of instances, and Snowflake IDs are ordered as far as the host clocks agree. No distributed generator gives strict global order without a single point of coordination; use a database sequence if you need it.

## Why are there gaps?

A segment generator skips the rest of its segments when it restarts or a segment expires, and the chain prefetches segments it may not use. Snowflake IDs leave out the sequences no request used. Gaps are expected; do not use the IDs to count rows.

## How does the prefetch worker keep the safe distance?

- **Periodically**: every `prefetch-period` (1 second), it checks the chain and takes segments until `safe-distance` of them are ready.
- **On hunger**: a thread that finds no segment takes one itself and wakes the worker, which doubles its prefetch distance (up to `max-prefetch-distance`) and takes that many segments in one store call. The distance halves again when demand drops.

## Why keep IDs as close to monotonic as possible if trend increasing is enough?

Trend increasing is a trade-off: disorder within a window is the price of not coordinating every ID. The smaller the window, the closer inserts stay to the end of the index, so CosId keeps it small (small steps, prefetch instead of large segments).

## Can two applications share a store?

Yes. Give each its own `cosid.namespace`: counters and machine ids are scoped by namespace.

## What happens when the store is down?

Generation continues from memory: a segment chain uses its reserved segments, and Snowflake generators need the store only at startup and for lease renewal. About 4 minutes 50 seconds after the last successful renewal (`safe-guard-duration - min(10s, safe-guard-duration / 10)`), Snowflake generators and `CosIdGenerator` stop with `MachineIdLostException` rather than risk duplicates. Instances cannot start while the store is down.

If you have another question, ask in [Issues](https://github.com/Ahoo-Wang/CosId/issues).
