# CosId Proxy

The CosId proxy is a small HTTP server that stands between applications and the store. Applications lease machine ids and take segments from the proxy, and only the proxy holds the store's address and credentials. IDs are still generated inside each application; the proxy is called only for what a store would be called for.

<p align="center">
  <img src="../../../public/assets/design/CosId-Proxy.png" alt="CosId Proxy"/>
</p>

```
┌──────────────────────────┐      HTTP       ┌───────────────────┐       ┌────────────────────┐
│ Application              │ ──────────────► │ cosid-proxy-server│ ────► │ Redis / JDBC / ... │
│ ProxyMachineLeaseStore   │                 │                   │       │                    │
│ ProxyIdSegmentDistributor│                 │                   │       │                    │
└──────────────────────────┘                 └───────────────────┘       └────────────────────┘
```

Use it when applications must not reach the store directly, for example across network zones, or to keep one set of store credentials. It adds a service to run and secure, and a network hop at startup, at lease renewal and at segment prefetch.

## Running the server

The server is `cosid-proxy-server`, a Spring Boot application published as the Docker image `ahoowang/cosid-proxy`. It listens on port `8688` and uses Redis by default; configure its store exactly like an application's, with `cosid.machine.distributor.type` and `cosid.segment.distributor.type`.

```shell
docker run --name cosid-proxy -d -p 8688:8688 --link redis \
  -e SPRING_DATA_REDIS_URL=redis://redis:6379 \
  -e COSID_PROXY_TOKEN=change-me \
  ahoowang/cosid-proxy:__COSID_VERSION__
```

Use `--network` (or `--link redis`) so that the container can reach Redis, and a released image tag, such as `__COSID_VERSION__` (the current release) or `latest`.

The server refuses to start without a token, see [Authentication](#authentication).

Point container and orchestrator health checks at `/actuator/health/liveness`, as the image's `HEALTHCHECK` does: it answers without asking the store. `/actuator/health` includes the store, so it reports `DOWN` while the store is down and can wait as long as the store's health check when the store hangs: use it for alerts. The default `/actuator/health/readiness` group does not include the store either.

The image includes the drivers of every store: Redis, MySQL, PostgreSQL, MongoDB and ZooKeeper. Redis is configured by default; `DataSourceAutoConfiguration` and `MongoAutoConfiguration` are in `spring.autoconfigure.exclude`, so that an unused store does not show up in the health check. To use another store, set the exclusions so that only the chosen store is configured: otherwise the health check reports the unused Redis as `DOWN`, the status is `DOWN` and the container is marked unhealthy. For PostgreSQL:

```shell
docker run --name cosid-proxy -d -p 8688:8688 \
  -e COSID_PROXY_TOKEN=change-me \
  -e SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/cosid_db \
  -e SPRING_DATASOURCE_USERNAME=cosid -e SPRING_DATASOURCE_PASSWORD=change-me \
  -e COSID_MACHINE_DISTRIBUTOR_TYPE=jdbc -e COSID_SEGMENT_DISTRIBUTOR_TYPE=jdbc \
  -e SPRING_APPLICATION_JSON='{"cosid": {...}}' \
  ahoowang/cosid-proxy:__COSID_VERSION__
```

The JDBC defaults are MySQL statements: for PostgreSQL, create the tables (or set `enable-auto-init-*` with the PostgreSQL table statements) and override the segment statements as in [JDBC: PostgreSQL](./jdbc.md#postgresql), for example in `SPRING_APPLICATION_JSON` or a mounted `config/application.yaml`. The `SPRING_AUTOCONFIGURE_EXCLUDE` value replaces the whole default list, so it must name every store not in use:

| Store | `SPRING_AUTOCONFIGURE_EXCLUDE` |
|-------|--------------------------------|
| Redis (default) | `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration` |
| JDBC (MySQL, PostgreSQL) | `org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration` |
| MongoDB (also set `spring.mongodb.uri`) | `org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration` |
| ZooKeeper | `org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration` |

In the image, the heap is 75% of the container's memory limit (`-XX:MaxRAMPercentage=75.0`); to pin it, set `JAVA_OPTS`, for example `-e JAVA_OPTS=-Xmx1g`. The start scripts of the distribution outside the image keep a fixed 1280M heap. `GET /ids` answers with `Cache-Control: no-store`, so that no cache between a client and the server hands out the same id twice.

## Client

### Installation

::: code-group
```kotlin [Gradle (Kotlin)]
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation(platform("me.ahoo.cosid:cosid-dependencies:$cosidVersion")) // manages the CoApi version
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-proxy")
    implementation("me.ahoo.coapi:coapi-spring-boot-starter")
}
```
```xml [Maven]
<dependencies>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-proxy</artifactId>
    </dependency>
    <dependency>
        <groupId>me.ahoo.coapi</groupId>
        <artifactId>coapi-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```
:::

With Maven, import `me.ahoo.cosid:cosid-dependencies` as a BOM as well, for the CoApi version.

### Configuration

```yaml
cosid:
  namespace: ${spring.application.name}
  proxy:
    host: http://cosid-proxy:8688
    token: ${COSID_PROXY_TOKEN}
  machine:
    enabled: true
    distributor:
      type: proxy
  segment:
    enabled: true
    distributor:
      type: proxy
```

| Property | Default | Description |
|----------|---------|-------------|
| `cosid.proxy.host` | `http://localhost:8688` | Base URL of the proxy server |
| `cosid.proxy.token` | none | Bearer token sent with every request |
| `cosid.proxy.connect-timeout` | `2s` | Connecting to the server; `0` means no timeout |
| `cosid.proxy.read-timeout` | `5s` | Waiting for a response, including the machine id claim at startup; `0` means no timeout, but with `cosid.machine.distributor.type=proxy` it must be positive |
| `cosid.proxy.client` | `coapi` | `coapi` or `http-interface`, see below; `http-interface` is used without CoApi on the classpath |

The client side is a `MachineLeaseStore` and an `IdSegmentDistributor` like any other: `ProxyMachineLeaseStore` calls the server's lease API (`/machines/v2`) to claim, renew and release machine ids, and `ProxyIdSegmentDistributor` asks it for segments. By default the clients are created by CoApi, which resolves `lb://` hosts and applies `coapi.*` settings and your CoApi customizers. With `cosid.proxy.client=http-interface`, or without CoApi on the classpath, they are plain beans built with Spring's `HttpServiceProxyFactory` from your `RestClient.Builder`, with the host, timeouts and token above; Spring AOT processing supports this mode.

## Authentication

The server needs at least one bearer token, and fails to start without one. Configure tokens on the server and the same token on every client:

```yaml
# proxy server
cosid:
  proxy:
    server:
      auth:
        tokens:
          - ${COSID_PROXY_TOKEN}   # list several tokens to rotate them
        public-paths:              # need no token; default: the health endpoint
          - /actuator/health
          - /actuator/health/**
```

Every request without a valid `Authorization: Bearer <token>` gets `401`, including the actuator endpoints (some of them generate or remove IDs) and the API docs, except the public paths. Public paths are path patterns matched on the decoded path; by default they are the health endpoint under `management.endpoints.web.base-path`. To open the API docs, add `/v3/api-docs/**`, `/swagger-ui.html` and `/webjars/swagger-ui/**` (the server uses springdoc on WebFlux).

`cosid.proxy.server.auth.allow-anonymous: true` runs the server without tokens; use it only behind a trusted network boundary.

## Server limits

The server checks what clients send, and answers `400` to requests outside these limits:

| Property | Default | Description |
|----------|---------|-------------|
| `cosid.proxy.server.min-safe-guard-duration` | `5m` | A client may take over a machine id only if its lease was last renewed at least this long ago, by the store's clock. 4.0 clients that send a later cut-off get `400`; a shorter guard from a 3.x client is raised to this value with a warning. Keep it at most the clients' `cosid.machine.guarder.safe-guard-duration`; must not be negative |
| `cosid.proxy.server.max-clock-skew` | `1m` | How far ahead of the store's clock (the server's clock for the v1 API) a client's last timestamp may be; a timestamp far in the future would keep a machine id from ever being recycled. `0` disables the check; must not be negative |
| `cosid.proxy.server.segment.max-step` | `1000000000000` | Largest segment `step` a client may ask for; `0` means no limit. Segment chains ask for `step × segments` at once and more under load, so set it to at least the largest `step × max-prefetch-distance` of all clients. `offset` must be between `0` and `Long.MAX_VALUE / 2` |
| `cosid.proxy.server.segment.max-distributors` | `0` | Most segment distributors the server keeps in memory, one per namespace and name, shared by all namespaces; a request for a new one beyond it gets `429` instead of `400`. `0` means no limit; set it, for example to `10000`, when untrusted clients can reach the server |

## Deployment security

- Run the server on an internal network only, never exposed to the internet. Anyone who can call it can release a machine id that a live instance holds, and two instances then generate the same IDs.
- A token is a bearer credential: whoever has it can call every endpoint, for every namespace it may use. Give each application its own token limited to its namespace with `cosid.proxy.server.auth.clients`, and list the served namespaces in `cosid.proxy.server.namespaces`. Send tokens over HTTPS or inside a service mesh with mutual TLS (a client with a token and a plain `http` host other than localhost logs a warning), keep them in a secret store, and rotate them by listing the new and the old token together.
- Keep `cosid.proxy.server.segment.max-step` finite, so that one request cannot use up or overflow a counter.
- The server does not open JMX. To monitor it over JMX, pass the options in `JAVA_OPTS`, with authentication and TLS:

```shell
JAVA_OPTS="-Dcom.sun.management.jmxremote.port=5555 \
  -Dcom.sun.management.jmxremote.authenticate=true \
  -Dcom.sun.management.jmxremote.password.file=/opt/cosid-proxy-server/config/jmxremote.password \
  -Dcom.sun.management.jmxremote.access.file=/opt/cosid-proxy-server/config/jmxremote.access \
  -Dcom.sun.management.jmxremote.ssl=true \
  -Djavax.net.ssl.keyStore=/opt/cosid-proxy-server/config/jmx.p12 \
  -Djavax.net.ssl.keyStorePassword=${JMX_KEYSTORE_PASSWORD}"
```

Unauthenticated JMX gives full control of the JVM; a heap dump also exposes the tokens and the store passwords.

## Monitoring

The server exposes Prometheus metrics at `/actuator/prometheus`, such as the JVM, the HTTP requests per API path and status, and the [CosId meters](../operations/monitoring.md#metrics) of its own generators and machine id lease. Like every endpoint other than health, it needs the token:

```yaml
scrape_configs:
  - job_name: cosid-proxy
    metrics_path: /actuator/prometheus
    authorization:
      credentials: change-me   # the server's token
    static_configs:
      - targets: ['cosid-proxy:8688']
```

## Availability

The server keeps no client state of its own: leases and counters live in its store. Run several servers behind a load balancer for availability. After a server restarts, running clients continue without a restart: the server rebuilds its segment distributors from the store on the next request.

## Upgrading from 3.x

1. Set `cosid.proxy.token` on the 3.4.x clients (3.4 sends it, and a server without tokens ignores it).
2. Upgrade the servers, with the same token in `cosid.proxy.server.auth.tokens`. Upgrading them before the clients have the token gives those clients `401`.
3. Upgrade the clients. Keep the server's `min-safe-guard-duration` at most the clients' `cosid.machine.guarder.safe-guard-duration`.

4.0 servers keep the deprecated v1 machine API (`/machines/{namespace}`) for 3.x clients, and 4.0 clients use the v2 lease API, which 3.x servers do not have. See [Migrating to 4.0](../migration/4.0.md).
