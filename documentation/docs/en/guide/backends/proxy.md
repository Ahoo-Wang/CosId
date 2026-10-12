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
  ahoowang/cosid-proxy:4.0.0-RC2
```

Use `--network` (or `--link redis`) so that the container can reach Redis, and a released image tag (4.0.0-RC2 at the time of writing).

The server refuses to start without a token, see [Authentication](#authentication).

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

The client side is a `MachineLeaseStore` and an `IdSegmentDistributor` like any other: `ProxyMachineLeaseStore` calls the server's lease API (`/machines/v2`) to claim, renew and release machine ids, and `ProxyIdSegmentDistributor` asks it for segments. Ahead-of-time processing and native images do not support the proxy client yet; run applications that use it on the JVM.

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

## Deployment security

- Run the server on an internal network only, never exposed to the internet. Anyone who can call it can release a machine id that a live instance holds, and two instances then generate the same IDs.
- A token is a bearer credential: whoever has it can call every endpoint, for every namespace. Send it over HTTPS or inside a service mesh with mutual TLS, keep it in a secret store, and rotate it by listing the new and the old token together.
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

## Availability

The server keeps no client state of its own: leases and counters live in its store. Run several servers behind a load balancer for availability. After a server restarts, running clients continue without a restart: the server rebuilds its segment distributors from the store on the next request.

## Upgrading from 3.x

Upgrade the servers before the clients. 4.0 servers keep the deprecated v1 machine API (`/machines/{namespace}`) for 3.x clients, and 4.0 clients use the v2 lease API, which 3.x servers do not have. See [Migrating to 4.0](../migration/4.0.md).
