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
| `cosid.proxy.server.auth.public-paths` | `List<String>` | the health endpoint | Path patterns that need no token. By default `{base-path}/health` and `{base-path}/health/**`, with `management.endpoints.web.base-path` |
| `cosid.proxy.server.auth.allow-anonymous` | `boolean` | `false` | Run without tokens; only behind a trusted network boundary |
| `cosid.proxy.server.min-safe-guard-duration` | `Duration` | `5m` | A client may take over a machine id only if its lease was last renewed at least this long ago. Keep it at most the clients' `cosid.machine.guarder.safe-guard-duration`; must not be negative |
| `cosid.proxy.server.max-clock-skew` | `Duration` | `1m` | How far ahead of the store's clock (the server's clock for the v1 API) a client's last timestamp may be; `0` disables the check; must not be negative |
| `cosid.proxy.server.segment.max-step` | `long` | `1000000000000` | Largest segment step a client may ask for; `0` means no limit |
| `cosid.proxy.server.blocking.threads` | `int` | `64` | Threads that run API calls, which wait on the store, so a slow store never stalls the event loop that answers health checks. With `spring.threads.virtual.enabled=true` (Java 21+), API calls run on virtual threads instead, without this pool or its `503` |
| `cosid.proxy.server.blocking.queue-capacity` | `int` | `1024` | API calls waiting for one of those threads; beyond it the server answers `503` with `Retry-After: 1` at once |

Requests outside the limits get `400`; requests without a valid token get `401`; requests beyond the thread pool, or beyond `cosid.max-concurrent-calls` store calls, get `503` and can be retried.
