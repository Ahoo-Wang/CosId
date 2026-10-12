# Proxy Configuration

See [CosId Proxy](../../guide/backends/proxy.md) for how the proxy works and how to deploy it.

## Client

> `me.ahoo.cosid.spring.boot.starter.ProxyProperties`, prefix `cosid.proxy`

Used when `cosid.machine.distributor.type` or `cosid.segment.distributor.type` is `proxy`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `host` | `String` | `http://localhost:8688` | Base URL of the proxy server |
| `token` | `String` | none | Bearer token sent with every request; must match one of the server's tokens |
| `connect-timeout` | `Duration` | `2s` | Connecting to the server |
| `read-timeout` | `Duration` | `5s` | Waiting for a response, including the machine id claim at startup; must be positive |

## Server

Properties of `cosid-proxy-server`, besides the usual `cosid.machine` and `cosid.segment` properties that choose its store.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `cosid.proxy.server.auth.tokens` | `List<String>` | empty | Accepted bearer tokens; list several to rotate them. The distribution reads `COSID_PROXY_TOKEN`. Without a token and without `allow-anonymous`, the server fails to start |
| `cosid.proxy.server.auth.public-paths` | `List<String>` | the health endpoint | Path patterns that need no token. By default `{base-path}/health` and `{base-path}/health/**`, with `management.endpoints.web.base-path` |
| `cosid.proxy.server.auth.allow-anonymous` | `boolean` | `false` | Run without tokens; only behind a trusted network boundary |
| `cosid.proxy.server.min-safe-guard-duration` | `Duration` | `5m` | A client may take over a machine id only if its lease was last renewed at least this long ago. Keep it at most the clients' `cosid.machine.guarder.safe-guard-duration`; must not be negative |
| `cosid.proxy.server.max-clock-skew` | `Duration` | `1m` | How far ahead of the server clock a client's last timestamp may be; `0` disables the check; must not be negative |
| `cosid.proxy.server.segment.max-step` | `long` | `1000000000000` | Largest segment step a client may ask for; `0` means no limit |

Requests outside the limits get `400`; requests without a valid token get `401`.
