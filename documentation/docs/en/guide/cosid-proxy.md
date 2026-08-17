# CosId Proxy Module

<center>

![CosId Proxy](../../public/assets/design/CosId-Proxy.png)
</center>

CosId Proxy enables remote ID distribution, allowing clients to distribute machine IDs and segment IDs through a proxy server instead of directly connecting to the underlying storage.

## Overview

CosId Proxy provides a client-server architecture where:

- **Proxy Server**: Manages the actual distribution of machine IDs and segment IDs using underlying storage (Redis, ZooKeeper, JDBC, etc.)
- **Proxy Client**: Uses `ProxyMachineIdDistributor` and `ProxyIdSegmentDistributor` to communicate with the proxy server via HTTP/REST API

This architecture is useful when:
- Direct access to storage infrastructure is restricted
- Centralized ID management is required
- Enhanced security and auditing are needed

## Key Components

### ProxyMachineIdDistributor

`ProxyMachineIdDistributor` is a remote implementation of `MachineIdDistributor` that communicates with the proxy server for:
- Distributing machine IDs
- Reverting machine IDs
- Guarding machine IDs (heartbeat)

### ProxyIdSegmentDistributor

`ProxyIdSegmentDistributor` is a remote implementation of `IdSegmentDistributor` that communicates with the proxy server for:
- Allocating ID segments
- Managing segment chains

## Architecture

```
┌─────────────────┐         HTTP/REST          ┌─────────────────┐
│  CosId Client   │ ◄──────────────────────► │  Proxy Server   │
│                 │                           │                 │
│ ProxyMachineId  │                           │ Redis/JDBC/ZK   │
│ ProxyIdSegment  │                           │                 │
└─────────────────┘                           └─────────────────┘
```

## Use Cases

- **Security-restricted environments**: When client applications cannot directly access storage infrastructure
- **Centralized management**: When you need centralized control over ID generation
- **Multi-tenant systems**: When you want to isolate ID generation per tenant through the proxy

## High Availability: Server-Restart Self-Healing

The proxy server's segment distributor cache lives in memory only. Since clients call `createDistributor` exactly once at startup, a server restart leaves the cache empty; on the next `nextMaxId` call the distributor is rebuilt lazily from the backing store (lazy rebuild uses the default `offset=0`, which only applies on first-ever initialization), so already-allocated segment state is never lost. In short: **after a proxy server restart, running clients recover automatically without a restart** (since v3.2.1).
