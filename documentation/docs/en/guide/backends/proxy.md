# CosId Proxy Module

<center>

![CosId Proxy](../../../public/assets/design/CosId-Proxy.png)
</center>

CosId Proxy enables remote ID distribution, allowing clients to distribute machine IDs and segment IDs through a proxy server instead of directly connecting to the underlying storage.

## Overview

CosId Proxy provides a client-server architecture where:

- **Proxy Server**: Manages the actual distribution of machine IDs and segment IDs using underlying storage (Redis, ZooKeeper, JDBC, etc.)
- **Proxy Client**: Uses `ProxyMachineLeaseStore` and `ProxyIdSegmentDistributor` to communicate with the proxy server via HTTP/REST API

This architecture is useful when:
- Direct access to storage infrastructure is restricted
- Centralized ID management is required
- Enhanced security and auditing are needed

## Key Components

### ProxyMachineLeaseStore

`ProxyMachineLeaseStore` is a remote `MachineLeaseStore`. The client wraps it in a `LeaseStoreMachineIdDistributor`, like any other backend, and it calls the server's lease API (`/machines/v2`) to:
- Claim machine IDs (a new one, or one released or expired)
- Renew a lease (heartbeat)
- Release a lease

The server keeps the v1 machine API (`/machines/{namespace}`) for 3.x clients. It is deprecated, and 3.x servers do not have the v2 API, so upgrade the servers before the clients.

### ProxyIdSegmentDistributor

`ProxyIdSegmentDistributor` is a remote implementation of `IdSegmentDistributor` that communicates with the proxy server for:
- Allocating ID segments
- Managing segment chains

## Architecture

```
┌─────────────────┐         HTTP/REST          ┌─────────────────┐
│  CosId Client   │ ◄──────────────────────► │  Proxy Server   │
│                 │                           │                 │
│ ProxyLeaseStore │                           │ Redis/JDBC/ZK   │
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
| `cosid.proxy.server.min-safe-guard-duration` | `5m` | A client may take over a machine ID only if its lease was last renewed at least this long ago, by the server's store clock. Clients that send a later cut-off get `400`; a shorter guard from a 3.x client is raised to this value with a warning. Keep it at most the clients' `cosid.machine.guarder.safe-guard-duration` |
| `cosid.proxy.server.max-clock-skew` | `1m` | A client's `lastTimeStamp` (guard, revert) may be at most this far ahead of the server clock; a timestamp far in the future would keep a machine ID from ever being recycled. `0` disables the check |
| `cosid.proxy.server.segment.max-step` | `1000000000000` | Largest segment `step` a client may ask for; `0` means no limit. Segment chains ask for `step × segments` at once and prefetch more under load, so set it to at least the largest `step × max prefetch distance` of all clients. `offset` must be between `0` and `Long.MAX_VALUE / 2` |

Requests outside these limits get `400`.

## High Availability: Server-Restart Self-Healing

The proxy server's segment distributor cache lives in memory only. Since clients call `createDistributor` exactly once at startup, a server restart leaves the cache empty; on the next `nextMaxId` call the distributor is rebuilt lazily from the backing store (lazy rebuild uses the default `offset=0`, which only applies on first-ever initialization), so already-allocated segment state is never lost. In short: **after a proxy server restart, running clients recover automatically without a restart** (since v3.2.1).

## Authentication

The proxy server needs at least one bearer token: without any, it fails to start (since 4.0). Configure one or more tokens on the server, and the same token on every client:

```yaml
# proxy server
cosid:
  proxy:
    server:
      auth:
        tokens:
          - ${COSID_PROXY_TOKEN}   # several tokens can be listed to rotate them
        public-paths:              # need no token; default: the health endpoint under management.endpoints.web.base-path
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

## Deployment Security

- Run the proxy server on an internal network only, never exposed to the internet. Anyone who can call it can release a machine ID that a live instance holds, and two instances then generate the same IDs.
- A token is a bearer credential: whoever has it can call every endpoint, for every namespace. Send it over HTTPS or inside a service mesh with mutual TLS, keep it in a secret store, and rotate it by listing the new and the old token together.
- Keep `cosid.proxy.server.segment.max-step` finite, so that one request cannot use up or overflow a segment.
- `cosid.proxy.server.auth.allow-anonymous=true` runs the server without tokens; use it only behind a trusted network boundary.

### JMX

The server does not open JMX. To monitor it over JMX, pass the options in `JAVA_OPTS`, with authentication and TLS:

```shell
JAVA_OPTS="-Dcom.sun.management.jmxremote.port=5555 \
  -Dcom.sun.management.jmxremote.authenticate=true \
  -Dcom.sun.management.jmxremote.password.file=/opt/cosid-proxy-server/config/jmxremote.password \
  -Dcom.sun.management.jmxremote.access.file=/opt/cosid-proxy-server/config/jmxremote.access \
  -Dcom.sun.management.jmxremote.ssl=true \
  -Djavax.net.ssl.keyStore=/opt/cosid-proxy-server/config/jmx.p12 \
  -Djavax.net.ssl.keyStorePassword=${JMX_KEYSTORE_PASSWORD}"
```

Unauthenticated JMX gives full control of the JVM; a heap dump also exposes the tokens and the database passwords.
