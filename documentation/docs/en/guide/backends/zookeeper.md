# ZooKeeper

`cosid-store-zookeeper`: `RangeStore` and `SlotLeaseStore` on Curator 5.9. **ZooKeeper 3.8 or later** (D27), 3.9 the
primary target; CI runs the TCK on both. 3.7.2 and 3.6.4 passed the TCK once (not a support commitment). 3.5 and older
are expected to fail, because acquire reads slots with a read-only `multi`.

## Configuration

```yaml
cosid:
  stores:
    zk:
      type: zookeeper
      timeout: 3s                    # each store operation as a whole, compare-and-set retries included
      options:
        connectString: zk-1:2181,zk-2:2181,zk-3:2181
```

Either Spring's (or your) started `CuratorFramework` is the client, and the caller closes it; or the store builds one
from the options and closes it:

| Option | Default |
|---|---|
| `connectString` | `localhost:2181` |
| `sessionTimeout` | 60s |
| `connectionTimeout` | 15s |
| `blockUntilConnectedWait` | 10s |
| `retry.baseSleepTime` / `retry.maxRetries` / `retry.maxSleep` | 100ms / 5 / 500ms |

## Nodes

| Node | Data |
|---|---|
| `/cosid/range/{key}` | the next value as decimal text, `:sealed` appended once sealed; never deleted, and it **must not be deleted while any store uses the key** (`purge` of the migration tool runs only with every instance stopped): a store compare-and-sets on the version it last wrote, and a node recreated to that version is detected by its `czxid` and refused, but the write itself cannot be taken back |
| `/cosid/slot/{ns}` | the namespace's `limit` and `tolerance` |
| `/cosid/slot/{ns}/{slot}` | the slot as `name=value` lines: owner key, incarnation, last owner key, **fence**, ttl, tolerance, floor, expiry and ceiling lower bounds, next floor |
| `/cosid/slot/{ns}/.clock` | written by each acquire; its `mtime` is the `now` of the expiry check |
| `/cosid/owner/{ns}/{ownerKey}` | the slots last owned by the key, written in the same `multi` as each acquire |

- **The fence is in the data**, not `Stat.version`; the version only implements the compare-and-set. So a recreated node
  cannot move the fence back, and run-time code never deletes a slot node.
- Lease times come from the `mtime` of the write that produced them: `expiresAt = max(expiresMin, mtime + ttl)` and
  `ceiling = max(ceilingMin, expiresAt + tolerance)`. The lower bounds come from the replaced write, so neither goes
  back within a term.
- An acquire reads the clock and the owner index, then only the slots it lists and the preferred one. The lowest free
  slot is searched in growing batches (32, 64, … up to 256 slots per read-only `multi`). Only an exhausted namespace
  reads every slot.
- Namespaces cannot contain `/`; a range key must be a valid ZooKeeper path.

## Operational precondition: rebuilding slot nodes {#rebuild}

::: warning Manual rebuild of slot nodes (人工重建槽位节点)
If slot nodes are ever restored or rebuilt by hand (a backup restore, a moved ensemble), **write them on a server whose
clock is not behind the original nodes' `mtime`**. Lease times derive from `mtime`, so a restore under a clock that
lags could hand a window that overlaps the previous holder's. If you cannot ensure that, **wait one ttl (plus
tolerance) after the restore before starting any client**.
:::
