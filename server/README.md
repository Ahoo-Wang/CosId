# cosid-server

The CosId 5 remote service (`docs/architecture/08-remote.md`, D8). It is a Spring Boot application, not a library. It
exposes the two store primitives of its **backing store** at the root of the server (no version prefix, D30): one of `cosid.stores`, either JDBC, Redis, Mongo
or ZooKeeper. Each call is one call of that store. The store runs the slot choice (05 §2.1) and stamps every time with its
own clock, so the server keeps no state and scales out. `cosid-store-remote` (type `remote`) is the client.

Optionally (`cosid.server.ids.enabled=true`), the server also issues ids from the generators of its own `CosIdRuntime`.
This is for clients that cannot reach a store or are not on the JVM.

## API

Keys and namespaces travel in the JSON body, never in the path. Errors are `{code, message}`; the codes are in
`me.ahoo.cosid.store.remote.RemoteApi`.

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/ranges:reserve` | `{key, size, initialOffset}` | `{start, end}`; `410 range_sealed`; `422 range_exhausted` |
| POST | `/ranges:seal` | `{key}` | `204`; `501 seal_unsupported` |
| POST | `/slots:acquire` | `{namespace, ownerKey, incarnation, limit, ttlMs, toleranceMs, preferredSlot?}` | lease + `{reason, duplicateOwnerKey}`; `409 slots_exhausted {earliestExpiresAt}`; `409 namespace_mismatch` |
| POST | `/slots:renew` | `{namespace, slot, ownerKey, incarnation, fence, ttlMs, toleranceMs}` | lease; `409 lease_lost` |
| POST | `/slots:release` | `{namespace, slot, ownerKey, incarnation, fence, lastIssued}` | `204`; `409 lease_lost` |
| GET | `/ids[/{generator}]?count=n` | — | `{ids: [...]}` (optional) |
| GET | `/ids[/{generator}]/strings?count=n` | — | `{ids: ["..."]}`: the text ids of any strategy (optional): `long` ids through the generator's codec, cosid80, periodic segment and uuidv7 text |
| GET | `/ids/{generator}/periodic?count=n` | — | `{period, values: [...]}`, all of one period (optional) |

A lease is `{namespace, slot, ownerKey, incarnation, fence, expiresAt, floor, ceiling, storeNow}`. Renew and release
also accept the lease's `expiresAt`, `floor`, `ceiling` and `storeNow`, and the client sends them. Without a generator in
the path, the id endpoints use the one named `default` (the 4.x share).

Other statuses: `400 bad_request` (a missing field, a bad character set or length, a value past a limit), `401`, `403`,
`429 too_many_keys` / `too_many_namespaces`, `503 busy` with `Retry-After: 1`, `504 store_timeout`, `500 store_error`.
Failures of the backing store answer a generic message; their details go to the log. Every response carries
`Cache-Control: no-store`.

## Configuration (`cosid.server.*`)

| Property | Default | Meaning |
|---|---|---|
| `store` | the only store | the backing store among `cosid.stores` |
| `auth.tokens` | — | tokens that may use everything |
| `auth.clients[i].{name, token, namespaces, key-prefixes}` | — | tokens limited to slot namespaces, range keys whose first segment is one of the namespaces, or range keys with one of the prefixes; `*` allows every namespace; the id endpoints need `cosid.namespace`. A client with a named machine leases its slot in the machine's own namespace, so list both, such as `[order-service, global-ids]`, or it gets `403` |
| `auth.allow-anonymous` | `false` | without any token, the server **refuses to start** unless this is set; tokens shorter than 16 characters or holding `${` (an unresolved placeholder) are refused too. Anonymous opens **everything** to every caller, Actuator's `cosid` endpoint included: only on a trusted network (a `ClusterIP` Service behind a `NetworkPolicy`, no Ingress); see the guide |
| `auth.public-paths` | `/actuator/health`, `/actuator/health/**` | path patterns that need no token |
| `docs.public` | `false` | adds the springdoc paths (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**`, `/v3/api-docs.yaml`, following `springdoc.*.path`) to the public paths, on top of `auth.public-paths`, so a browser shows Swagger UI without a token; its "Try it out" calls still need one (Authorize), and Actuator still needs a token of `tokens` |
| `limits.min-ttl` / `max-ttl` | `100ms` / `1h` | the lease ttl range |
| `limits.max-tolerance` | `1m` | |
| `limits.max-slot-limit` | `65536` | the stores prepare `limit` slots per namespace |
| `limits.max-reserve` | `1e12` | the largest `size` |
| `limits.max-count` | `1000` | the largest `count` of the id endpoints |
| `limits.max-keys-per-namespace` | `10000` | range keys one client uses in one namespace, per server instance (`429 too_many_keys`; `0` = no cap) |
| `limits.max-namespaces-per-client` | `1000` | namespaces one client uses range keys in, per server instance; a key without `/` is a namespace of its own (`429 too_many_namespaces`; `0` = no cap) |
| `limits.key-idle-timeout` | `1h` | a key unused this long no longer counts; failed reserves never count (`0` = keep every key) |
| `blocking.threads` / `queue-capacity` | `64` / `1024` | the bounded pool of the store calls |
| `ids.enabled` | `false` | the id endpoints; the server then refuses generators named `strings` or `periodic` (path segments) |

A JDBC backing store with `options.url` gets a HikariCP pool sized to `blocking.threads`. The runtime's generators on
the backing store (the id endpoints, the `cosid` endpoint) use that same store, not a second client.

A client token may use only the client paths: the store primitives and `/ids/**`, under `spring.mvc.servlet.path`.
Every other path, Actuator and springdoc wherever `management.endpoints.web.base-path` or `springdoc.*.path` put them,
needs a token of `auth.tokens`; a client token gets `403` there. (`auth.admin-paths` of 5.0.0-alpha.1 is gone.) The
server refuses a `management.server.port` other than `server.port`, which this authentication would not cover, unless
it is anonymous.

Requests run on virtual threads by default (`spring.threads.virtual.enabled: true`); the store and generator calls
still run on the bounded `blocking` pool, which is what bounds the store. `SPRING_THREADS_VIRTUAL_ENABLED=false` turns
it off.

## Operations

- Actuator: `health` (public; `liveness` and `readiness` probes; readiness also reserves on the backing store, key
  `cosid-server/readiness`, at most once a second, `DOWN` after 2s), `info`, `prometheus`, and `cosid` (the runtime).
  The meter `cosid.server.call.rejections{reason=busy|too_many_keys|too_many_namespaces}` counts the calls the server
  turned away. Actuator other than health needs a token of `auth.tokens`.
- OpenAPI: `/v3/api-docs` and Swagger UI at `/swagger-ui.html`; they need a token of `auth.tokens` unless `docs.public=true`. The document
  lists the Actuator endpoints too (`springdoc.show-actuator`); set it to `false` to leave them out.
- Image: `./gradlew :server:dockerImage` (the `Dockerfile` on `build/libs/cosid-server.jar`), or `:server:bootBuildImage`.

```bash
docker run -d -p 8688:8688 \
  -e COSID_STORES_MAIN_TYPE=redis -e COSID_STORES_MAIN_OPTIONS_URI=redis://redis:6379 \
  -e COSID_SERVER_AUTH_TOKENS_0=change-me-to-a-long-random-token ahoowang/cosid-server:5.0.0-alpha.2
```

Clients:

```yaml
cosid:
  stores:
    remote:
      type: remote
      options:
        baseUrl: http://cosid-server:8688
        token: change-me-to-a-long-random-token
        connectTimeout: 5s
        requestTimeout: 3s   # StoreConfig.timeout by default
```
