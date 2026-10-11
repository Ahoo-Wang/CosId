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

## Server Limits

The proxy server checks what clients send:

| Property | Default | Description |
|----------|---------|-------------|
| `cosid.proxy.server.max-clock-skew` | `1m` | A client's `lastTimeStamp` (guard, revert) may be at most this far ahead of the server clock; a timestamp far in the future would keep a machine ID from ever being recycled. `0` disables the check |
| `cosid.proxy.server.segment.max-step` | `0` (no limit) | Largest segment `step` a client may ask for. Segment chains ask for `step × segments` at once and prefetch more under load, so set it to at least the largest `step × max prefetch distance` of all clients. `offset` must be `>= 0` and `offset + step` must not overflow |

Requests outside these limits get `400`.

## High Availability: Server-Restart Self-Healing

The proxy server's segment distributor cache lives in memory only. Since clients call `createDistributor` exactly once at startup, a server restart leaves the cache empty; on the next `nextMaxId` call the distributor is rebuilt lazily from the backing store (lazy rebuild uses the default `offset=0`, which only applies on first-ever initialization), so already-allocated segment state is never lost. In short: **after a proxy server restart, running clients recover automatically without a restart** (since v3.2.1).

## Authentication

By default the proxy server is open to anyone who can reach it, and it logs a warning at startup.
Configure one or more bearer tokens on the server, and the same token on every client:

```yaml
# proxy server
cosid:
  proxy:
    server:
      auth:
        tokens:
          - ${COSID_PROXY_TOKEN}   # several tokens can be listed to rotate them
        public-paths:              # need no token; default: /actuator/health, /actuator/health/**
          - /actuator/health
          - /actuator/health/**
```

```yaml
# client application
cosid:
  proxy:
    host: http://cosid-proxy:8688
    token: ${COSID_PROXY_TOKEN}
```

With tokens configured, every request without a valid `Authorization: Bearer <token>` gets `401`, including the actuator endpoints (some of them generate or remove IDs) and the API docs, except the public paths. Public paths are path patterns matched on the decoded path. To open the API docs, add `/v3/api-docs/**` and `/swagger-ui/**`.
