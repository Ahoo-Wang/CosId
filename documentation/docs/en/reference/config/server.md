# Configuration Reference: cosid-server

`cosid.server.*`, bound by `ServerProperties` of cosid-server. Besides these, the server reads the ordinary `cosid.*`
properties ([runtime, stores, machine](basic.md), [generators](generators.md)): its backing store is one of
`cosid.stores`. Every property can be set from the environment, as in `COSID_SERVER_AUTH_TOKENS_0`, store options included
(`COSID_STORES_MAIN_OPTIONS_CONNECTSTRING` sets `connectString`: their case does not count).

| Property | Default | Meaning |
|---|---|---|
| `cosid.server.store` | the only store | the backing store among `cosid.stores`; required when there are several |
| `cosid.server.auth.allow-anonymous` | `false` | allow every call without a token, when no token and no client is configured; **without either, the server refuses to start** |
| `cosid.server.auth.tokens` | `[]` | tokens that may use every namespace and key |
| `cosid.server.auth.clients[i].name` | | the client, for logs |
| `cosid.server.auth.clients[i].token` | | its token |
| `cosid.server.auth.clients[i].namespaces` | `[]` | slot namespaces, and range keys whose first segment is one of them; `*` for all; the id endpoints check `cosid.namespace` |
| `cosid.server.auth.clients[i].key-prefixes` | `[]` | range keys with one of these prefixes |
| `cosid.server.auth.public-paths` | `/actuator/health`, `/actuator/health/**` | path patterns that need no token |
| `cosid.server.limits.min-ttl` | `100ms` | the shortest lease ttl |
| `cosid.server.limits.max-ttl` | `1h` | the longest lease ttl |
| `cosid.server.limits.max-tolerance` | `1m` | the largest tolerance |
| `cosid.server.limits.max-slot-limit` | `65536` | the largest slot limit of a namespace |
| `cosid.server.limits.max-reserve` | `1000000000000` | the largest range one reserve takes |
| `cosid.server.limits.max-count` | `1000` | the most ids one id request takes |
| `cosid.server.limits.max-keys-per-namespace` | `10000` | range keys one client uses in one namespace on one server instance; `0` for no cap |
| `cosid.server.limits.max-namespaces-per-client` | `1000` | namespaces one client uses range keys in on one server instance; `0` for no cap |
| `cosid.server.limits.key-idle-timeout` | `1h` | a range key unused this long no longer counts; `0` keeps every key |
| `cosid.server.blocking.threads` | `64` | the bounded pool the store calls run on |
| `cosid.server.blocking.queue-capacity` | `1024` | calls that may wait; beyond it `503` with `Retry-After: 1` |
| `cosid.server.ids.enabled` | `false` | the optional `/ids/**` endpoints; generators named `strings` or `periodic` are then refused |
| `cosid.server.docs.public` | `false` | the OpenAPI document and Swagger UI need no token (added to `auth.public-paths`); their "Try it out" calls still do |

A client token may use only the store primitives and `/ids/**` (under `spring.mvc.servlet.path`); every other path,
Actuator and springdoc included, needs a token of `tokens`. The server refuses a `management.server.port` other than
`server.port` unless it is anonymous. `auth.admin-paths` of 5.0.0-alpha.1 is gone: there is nothing to list any more.

The image's own defaults (`application.yaml`): `server.port: 8688`, requests on virtual threads
(`spring.threads.virtual.enabled: true`; `SPRING_THREADS_VIRTUAL_ENABLED=false` turns it off), the health probes on with
the backing store in the readiness group (the contributor `cosidServerStore`), and Actuator exposing `health`, `info`, `prometheus` and `cosid`. `cosid.namespace` defaults to `${spring.application.name}`
(`cosid-server`).

## From the 4.x proxy server

`cosid-migration config` reports every `cosid.proxy.server.*` key as dropped; their closest 5.0 settings:

| 4.x `cosid.proxy.server.*` | cosid-server |
|---|---|
| `namespaces` (the server serves these namespaces only) | no server-wide list: give every token as a client with its `namespaces` (`auth.clients[i]`); a token of `auth.tokens` reaches every namespace |
| `auth.*` | `auth.tokens`, `auth.clients[i]`, `auth.public-paths`, `auth.allow-anonymous` |
| `segment.max-step` | `limits.max-reserve` |
| `segment.max-distributors` | `limits.max-keys-per-namespace` (per client and namespace) |
| `min-safe-guard-duration` | `limits.min-ttl`: a lease is a time window, no guard |
| `max-clock-skew` | `limits.max-tolerance`: skew costs availability within the lease tolerance, never uniqueness (D22) |
| `blocking.*` | `blocking.threads`, `blocking.queue-capacity` |
