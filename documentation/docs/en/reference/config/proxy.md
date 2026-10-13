# Proxy Configuration

See [CosId Proxy](../../guide/backends/proxy.md) for how the proxy works and how to deploy it.

## Client

> `me.ahoo.cosid.spring.boot.starter.ProxyProperties`, prefix `cosid.proxy`

Used when `cosid.machine.distributor.type` or `cosid.segment.distributor.type` is `proxy`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `host` | `String` | `http://localhost:8688` | Base URL of the proxy server |
| `token` | `String` | none | Bearer token sent with every request; must match one of the server's tokens |
| `connect-timeout` | `Duration` | `2s` | Connecting to the server; `0` means no timeout |
| `read-timeout` | `Duration` | `5s` | Waiting for a response, including the machine id claim at startup; `0` means no timeout, but with `cosid.machine.distributor.type=proxy` it must be positive |

## Server

Properties of `cosid-proxy-server`, besides the usual `cosid.machine` and `cosid.segment` properties that choose its store.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `cosid.proxy.server.auth.tokens` | `List<String>` | empty | Accepted bearer tokens; list several to rotate them. The distribution reads `COSID_PROXY_TOKEN`. Without a token and without `allow-anonymous`, the server fails to start |
| `cosid.proxy.server.auth.clients` | `List<Client>` | empty | Tokens limited to namespaces: each entry has a `token` and the `namespaces` it may use; an entry without namespaces may use none (warned at startup). Use one per application, so a leaked token cannot release another application's machine ids. A limited token reaches only its namespaces and `/machines/v2/time`: not the actuator, the API docs or `/ids`, and no segment name containing a dot. Tokens in `tokens` may use every namespace and path |
| `cosid.proxy.server.auth.public-paths` | `List<String>` | the health endpoint | Path patterns that need no token. By default `{base-path}/health` and `{base-path}/health/**`, with `management.endpoints.web.base-path` |
| `cosid.proxy.server.auth.allow-anonymous` | `boolean` | `false` | Run without tokens; only behind a trusted network boundary |
| `cosid.proxy.server.min-safe-guard-duration` | `Duration` | `5m` | A client may take over a machine id only if its lease was last renewed at least this long ago. Keep it at most the clients' `cosid.machine.guarder.safe-guard-duration`; must not be negative |
| `cosid.proxy.server.max-clock-skew` | `Duration` | `1m` | How far ahead of the store's clock (the server's clock for the v1 API) a client's last timestamp may be; `0` disables the check; must not be negative |
| `cosid.proxy.server.namespaces` | `List<String>` | empty | Namespaces the server serves; a request for another one gets `403` whatever its token. Empty serves every namespace |
| `cosid.proxy.server.segment.max-step` | `long` | `1000000000000` | Largest segment step a client may ask for; `0` means no limit |
| `cosid.proxy.server.segment.max-distributors` | `int` | `0` | Most segment distributors (namespace and name pairs) the server keeps in memory, across all namespaces; a request for a new one beyond it gets `429`. `0` means no limit; set it, for example to `10000`, when untrusted clients can reach the server |

Requests outside the limits get `400`; requests without a valid token get `401`; requests for a namespace the token or the server does not allow get `403`; a new segment distributor beyond the limit gets `429`.

The namespace is read from the path of the machine API (v1 and v2) and the segment API. `/ids` serves the server's own namespace and needs a token from `tokens`. Stores key a segment as `namespace.name`, so a limited token, and any token when `cosid.proxy.server.namespaces` is set, cannot use segment names containing a dot: `/segments/a/b.c` would reach the counter `c` of namespace `a.b`. For the same reason, the namespaces of `auth.clients` and `cosid.proxy.server.namespaces` must not contain a dot: startup fails otherwise. For the same reason, avoid dotted segment names in a namespace whose name is a prefix of another namespace.

```yaml
cosid:
  proxy:
    server:
      auth:
        clients:
          - token: ${ORDER_SERVICE_TOKEN}
            namespaces: [order-service]
          - token: ${USER_SERVICE_TOKEN}
            namespaces: [user-service]
```
