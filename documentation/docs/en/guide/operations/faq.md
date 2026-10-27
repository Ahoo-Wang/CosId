# FAQ

**Is 5.0 compatible with 4.x?**
No (D1): not the API, the configuration, nor the stored data. The offline [migration tool](../migration/from-4x.md)
moves data and configuration once; 4.x and 5.0 never run in one namespace.

**Will ids continue after a migration, or jump?**
They continue. 5.0 keeps the 4.x default layout and epoch (D16), segments continue past the 4.x maximum, and time-based
ids continue after the migration time W.

**Do clocks have to be synchronized?**
Not for uniqueness: it rests on the store-side window invariant alone (D22). Skew costs availability. A host ahead of
the store by more than `tolerance` pauses near its window ceiling, and a host behind it waits longer to pass a new floor.
Run NTP anyway.

**What happens if my process freezes for a minute?**
When it resumes, its timestamps lie past its window ceiling: it refuses to issue (`SlotWindowExceededException`) until
it has renewed or acquired a new slot. Meanwhile another instance may hold the slot, with a window that starts after the
frozen one's ceiling.

**Can I keep issuing when the store is down?**
Segments: until the ids at hand and the ready ranges run out, which is about `lookahead` (1s) of traffic. A longer
`lookahead` buys more. Snowflake: until the window ceiling, about `ttl − ttl/3 + tolerance` after the last renewal.
There is no option to issue past a lost lease (P1).

**Why are there gaps in my segment ids?**
Gaps are allowed, duplicates never (D19). A restart drops the rest of a range, and so do a timed-out wait and a
`range-ttl` expiry.

**How many instances can share a namespace?**
`2^bits` with leased slots (1024 by default). Segments need no slot.

**Can I delete a generator at run time?**
No (D17): generators are fixed at start-up. Mark one `disabled: true`, or change the spec with a
`RuntimeSpecCustomizer` before start.

**How do I remove old period keys?**
Offline: `cosid-migration purge --to … --namespace … --before 20250101`, first with `--dry-run`. Run-time code never
deletes a key (D23).

**Which store should I use?**
JDBC if you have a database: transactional and simple. ZooKeeper if you run one. Redis for speed, with its durability
precondition. MongoDB on a replica set. cosid-server when applications should not reach the store directly, or are not
on the JVM.
