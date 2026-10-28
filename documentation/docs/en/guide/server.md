# cosid-server

cosid-server is CosId's remote service (D8). It is a Spring Boot application, shipped as a Docker image. It exposes the
two store primitives of its **backing store** (one of `cosid.stores`: JDBC, Redis, MongoDB or ZooKeeper) over HTTP,
and applications reach it through the `remote` store, `cosid-store-remote`:

```
application ──(cosid-store-remote, HTTP + token)──▶ cosid-server ──▶ JDBC | Redis | MongoDB | ZooKeeper
```

- Each HTTP call is one call of the backing store. The store runs the slot choice and stamps every time with its own
  clock, so **the server keeps no state** and scales out behind a load balancer. All consistency is the store's.
- Optionally (`cosid.server.ids.enabled=true`) the server issues ids from its own runtime. This serves clients that
  are not on the JVM, or that cannot reach a store.
- The 4.x proxy's v1 and v2 APIs are gone (D1), and the paths carry no version prefix (D30).

## Running it

```bash
docker run -d -p 8688:8688 \
  -e COSID_STORES_MAIN_TYPE=redis \
  -e COSID_STORES_MAIN_OPTIONS_URI=redis://redis:6379 \
  -e COSID_SERVER_AUTH_TOKENS_0=change-me-to-a-long-random-token \
  ahoowang/cosid-server:__COSID_VERSION__
```

Every setting can come from the environment (`COSID_SERVER_AUTH_TOKENS_0`) or a mounted `application.yaml`. The image
runs as a non-root user on port 8688; `/actuator/health/liveness` answers without asking the store and is the
container health check. `/actuator/health/readiness` also asks the backing store (a reserve of size 1 on the key
`cosid-server/readiness`, at most one at a time, its result reused for 1s, `DOWN` after 2s without an answer), so a
load balancer stops sending calls to a server whose store is down. A JDBC backing store with `options.url` gets a HikariCP pool sized to `blocking.threads`, and
the image carries the MySQL, MariaDB, PostgreSQL and SQL Server drivers.
[`examples/server/compose.yaml`](https://github.com/Ahoo-Wang/CosId/tree/main/examples/server) runs the server on Redis
with a client next to it. To build the image from source: `./gradlew :server:dockerImage` (or `:server:bootBuildImage`).

## Clients

```yaml
cosid:
  stores:
    remote:
      type: remote
      timeout: 2s
      options:
        baseUrl: http://cosid-server:8688
        token: ${COSID_SERVER_TOKEN}
        connectTimeout: 5s
        requestTimeout: 2s    # StoreConfig.timeout by default
  machine:
    leased:
      store: remote
  generators:
    order:
      strategy: segment
      store: remote
```

The client uses the JDK `HttpClient` and has no third-party dependency. Durations are a `Duration`, milliseconds,
ISO-8601, or `500ms` / `3s` / `1m` / `1h`. Answers that have an outcome map to it:

| Status | Client |
|---|---|
| `410 range_sealed` | `RangeSealedException` |
| `422 range_exhausted` | `RangeExhaustedException` |
| `409 slots_exhausted` | `AcquireResult.Exhausted` |
| `409 lease_lost` | an empty renew, a `false` release |
| `409 namespace_mismatch` | `NamespaceMismatchException` |
| `501` on seal | `false` |
| `504 store_timeout` | `RemoteStoreException` whose cause is a `StoreTimeoutException` (counted as a store timeout) |
| anything else | `RemoteStoreException` with `status()`, `code()`; `transientFailure()` for `503`, `504` and for calls with no answer |

## API

Keys and namespaces travel in the JSON body, never in the path. Errors are `{code, message}`.

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/ranges:reserve` | `{key, size, initialOffset}` | `{start, end}`; `410 range_sealed`; `422 range_exhausted` |
| POST | `/ranges:seal` | `{key}` | `204`; `501 seal_unsupported` |
| POST | `/slots:acquire` | `{namespace, ownerKey, incarnation, limit, ttlMs, toleranceMs, preferredSlot?}` | lease + `{reason, duplicateOwnerKey}`; `409 slots_exhausted {earliestExpiresAt}`; `409 namespace_mismatch` |
| POST | `/slots:renew` | `{namespace, slot, ownerKey, incarnation, fence, ttlMs, toleranceMs}` | lease; `409 lease_lost` |
| POST | `/slots:release` | `{namespace, slot, ownerKey, incarnation, fence, lastIssued}` | `204`; `409 lease_lost` |
| GET | `/ids[/{generator}]?count=n` | | `{ids: [...]}` (optional) |
| GET | `/ids[/{generator}]/strings?count=n` | | `{ids: ["..."]}` through the generator's codec (optional) |
| GET | `/ids/{generator}/periodic?count=n` | | `{period, values: [...]}`, all of one period (optional) |

A lease is `{namespace, slot, ownerKey, incarnation, fence, expiresAt, floor, ceiling, storeNow}`. Without a generator in
the path, the id endpoints use `default`. A `periodic` answer stops at a period boundary, so it may hold fewer than
`count` values; a disabled generator answers `409 generator_disabled`.

Other statuses: `400 bad_request` (a missing field, a bad character set or length, a value past a limit), `401`
(no token), `403` (a token outside its namespaces, or a client token outside the client paths), `429 too_many_keys` /
`too_many_namespaces`, `503 busy` with `Retry-After: 1`, `504 store_timeout` and `500 store_error`. Errors of the
backing store answer a generic message; the details go to the server log. Every response carries
`Cache-Control: no-store`. The OpenAPI document is at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`; both need an
unrestricted token unless `cosid.server.docs.public=true` (see below).

## Authentication

**Bearer tokens are on by default.** Without any token or client configured, the server refuses to start unless
`cosid.server.auth.allow-anonymous=true` (see [trusted networks](#trusted-networks-without-tokens)). It also refuses a
token shorter than 16 characters, and one that still holds `${`: an environment variable that was not set must not
leave the placeholder as a token anyone can read here.

```yaml
cosid:
  server:
    auth:
      tokens: [ "${COSID_SERVER_TOKEN}" ]          # may use everything
      clients:
        - name: orders
          token: ${COSID_ORDERS_TOKEN}
          namespaces: [ orders ]                   # slot namespaces, and range keys whose first segment is one of them
          key-prefixes: [ shared/orders- ]         # or range keys with one of these prefixes
      public-paths: [ /actuator/health, /actuator/health/** ]
```

A client token reaches only its namespaces (`*` for all) and key prefixes: anything else is `403`. The id endpoints
check `cosid.namespace` of the server's own runtime against the client's namespaces. A client token may use **only the
client paths**, the store primitives (`/ranges:*`, `/slots:*`) and `/ids/**`, under `spring.mvc.servlet.path` when it
is set. Every other path is `403` for it, whatever `management.endpoints.web.base-path` or `springdoc.*.path` move
Actuator and springdoc to: they ignore namespaces (the `cosid` endpoint issues ids of every generator), so only the
unrestricted `tokens` may use them. The public paths are checked first, so health stays open; they are matched as
written, so move them along when you move Actuator. A path with a `.` or `..` segment is `400`.

Actuator on a port of its own (`management.server.port` other than `server.port`) runs outside this authentication,
so the server refuses to start with it, unless it is anonymous anyway. Keep Actuator on the main port; health is
public there for the probes.

### Swagger UI in a browser

A browser sends no bearer token when it opens a page, so Swagger UI needs `cosid.server.docs.public=true`
(`COSID_SERVER_DOCS_PUBLIC=true`, default `false`). It adds the read-only springdoc paths (`/swagger-ui.html`,
`/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**`, `/v3/api-docs.yaml`, following `springdoc.*.path` and the
servlet path) to `public-paths` instead of replacing them, so health stays open. Anyone who reaches the server can then
read the API, with or without a token, Actuator included (`springdoc.show-actuator: true`; set it to `false` to leave
Actuator out), but calls nothing: "Try it out" needs a token entered with Authorize, checked as any other call, and
Actuator still needs an unrestricted token.

### Trusted networks without tokens

Inside a network you trust, such as a Kubernetes cluster, the server can run without tokens. Configure no token and
no client, and allow anonymous calls:

```yaml
# the server's container
env:
  - name: COSID_SERVER_AUTH_ALLOW_ANONYMOUS
    value: "true"
```

The clients then leave `token` out:

```yaml
cosid:
  stores:
    remote:
      type: remote
      options:
        baseUrl: http://cosid-server.cosid.svc:8688
```

An anonymous server answers every call as an unrestricted token would: **every caller reaches every namespace and
key, and every path, Actuator included**: the `cosid` endpoint issues ids of every generator, `prometheus` and
Swagger UI are open, and a token sent anyway is ignored. Run it this way only when the network is the boundary:

- expose it through a `ClusterIP` Service only, never through an Ingress, a `LoadBalancer` or a `NodePort`;
- restrict it with a `NetworkPolicy` to the namespaces of the applications that use it;
- once a caller outside them needs it, configure tokens instead (the server then needs one per caller, or one client
  token per scope).

## Limits

| Property | Default | |
|---|---|---|
| `limits.min-ttl` / `max-ttl` | `100ms` / `1h` | the lease ttl range |
| `limits.max-tolerance` | `1m` | |
| `limits.max-slot-limit` | `65536` | stores prepare `limit` slots per namespace |
| `limits.max-reserve` | `1e12` | the largest range size |
| `limits.max-count` | `1000` | the largest `count` of the id endpoints |
| `limits.max-keys-per-namespace` | `10000` | range keys one client uses in one namespace, per server instance (`429 too_many_keys`; `0` = no cap) |
| `limits.max-namespaces-per-client` | `1000` | namespaces one client uses range keys in, per server instance; a key without `/` is a namespace of its own (`429 too_many_namespaces`; `0` = no cap) |
| `limits.key-idle-timeout` | `1h` | a key unused this long no longer counts (`0` = keep every key) |
| `blocking.threads` / `queue-capacity` | `64` / `1024` | the bounded pool for store calls; when full, `503` + `Retry-After: 1` |

**Requests run on virtual threads** (`spring.threads.virtual.enabled: true` in the image's `application.yaml`), so
Tomcat's thread count no longer bounds them. The store and generator calls still run on the bounded pool above,
which is what bounds the load on the store. `SPRING_THREADS_VIRTUAL_ENABLED=false` goes back to Tomcat's platform
threads.

Each client counts on its own, so one cannot use up a shared namespace for the others, and a reserve that failed does
not count. The meter `cosid.server.call.rejections{reason=busy|too_many_keys|too_many_namespaces}` counts the calls
turned away. Actuator exposes
`health` (public, with `liveness` and `readiness` probes), `info`, `prometheus` and `cosid`.

## Issuing ids from the server

```yaml
cosid:
  namespace: shop
  server:
    ids:
      enabled: true
  generators:
    ticket:
      strategy: segment
      store: main
```

```bash
curl -H "Authorization: Bearer $TOKEN" 'http://cosid-server:8688/ids/ticket?count=10'
```

The server is then an ordinary `CosIdRuntime`: a time-based generator also needs `cosid.machine`, and each server
instance leases its own slot.

All `cosid.server.*` properties: [configuration reference](../reference/config/server.md).
