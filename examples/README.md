# CosId 5 examples

Three runnable Spring Boot applications and one plain-Java classpath test, not published. Each has a smoke test that
`./gradlew check` runs.

| Example | What it shows | Run |
|---|---|---|
| [`segment-jdbc`](segment-jdbc) | a `segment` generator (radix 62 + prefix codec), a `periodic-segment` per day, `@CosId` filling, on the JDBC store over the context's `DataSource` (H2 in memory, `initSchema: true`) | `./gradlew :examples:segment-jdbc:bootRun`, then `curl localhost:8080/orders/next`, `/bills/next` |
| [`snowflake-redis`](snowflake-redis) | Snowflake (`default`, radix 62), friendly trace ids and the 80-bit `cosid80` on one machine slot leased from Redis; the `cosId` health indicator and the `cosid` endpoint | `REDIS_URI=redis://localhost:6379 ./gradlew :examples:snowflake-redis:bootRun`, then `curl localhost:8080/ids/next`, `/actuator/health` |
| [`server`](server) | a client of cosid-server through the `remote` store (slot and segments over HTTP, bearer token), and the server's optional id endpoint | `./gradlew :server:dockerImage :examples:server:bootJar && docker compose -f examples/server/compose.yaml up` |
| [`plain-stores`](plain-stores) | no application, a test only (L14): `store/redis` and `store/zookeeper` on one plain-Java classpath with no Spring or BOM aligning versions; one Netty version (4.2), and a reserve through each store | `./gradlew :examples:plain-stores:test` (Docker: Redis and ZooKeeper in Testcontainers) |

The smoke tests need Docker only for `snowflake-redis` (a Redis in Testcontainers, or `COSID_TEST_REDIS_URI`) and
`plain-stores`. The
`server` test starts the built `cosid-server.jar` as its own process on an in-process ZooKeeper, because no project
may depend on `:server`.
