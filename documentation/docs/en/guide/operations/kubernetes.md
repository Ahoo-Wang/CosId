# Kubernetes

This page puts the pieces together for a cluster: where ids are generated, who holds the slots and ranges, how many
slots a Deployment needs, and how a pod starts and stops without losing ids or reusing them.

## The recommended architecture

```
┌──────── namespace shop ─────────┐      ┌──────── namespace cosid ────────┐
│ order-service pods              │      │ cosid-server pods (2+)          │
│   CosIdRuntime (in process)     │      │   stateless, ClusterIP Service  │
│   segment / snowflake / cosid80 │─────▶│   /ranges:*  /slots:*           │──▶ MySQL / Redis /
│   store: remote                 │ HTTP │                                 │    MongoDB / ZooKeeper
│ payment-service pods  ...       │─────▶│                                 │
└─────────────────────────────────┘      └─────────────────────────────────┘
```

- **Ids are generated in the business pods**, in their own `CosIdRuntime`. An id costs no network call: a segment
  takes values from a range it reserved, a time-based generator issues on the slot it leased.
- **cosid-server is the authority over leases and ranges.** The business pods reach it through the `remote` store, and
  it passes each call on to its backing store. It keeps no state, so it runs as an ordinary Deployment with two or more
  replicas. Only it holds database credentials; the business pods need none.
- **Each Deployment leases its slots** (`cosid.machine.leased`): a pod gets a free slot at start and releases it at
  stop, so pods need no fixed identity.
- **Ids unique across services** come from a [named machine](../concepts/slot-lease.md#named-machines) in a shared
  namespace (`cosid.machines.<name>.namespace`); each service keeps its own namespace for everything else.

A business pod can also use a store directly (`type: jdbc`, `redis`, …) and skip cosid-server. The server pays off when
many services share one store, when they should not hold its credentials, or when clients in other languages call
`/ids`.

## cosid-server

The example backs the server with MySQL and runs it **without tokens**, which is safe only inside a network you trust
(see [trusted networks](../server.md#trusted-networks-without-tokens)): a `ClusterIP` Service, never exposed outside the
cluster, and a `NetworkPolicy` that admits only the application namespaces. To use tokens instead, drop
`COSID_SERVER_AUTH_ALLOW_ANONYMOUS` and set `COSID_SERVER_AUTH_TOKENS_0` (at least 16 characters) from a Secret.

```yaml
apiVersion: v1
kind: Secret
metadata: { name: cosid-server-db, namespace: cosid }
stringData:
  url: jdbc:mysql://mysql.db.svc:3306/cosid_db
  username: cosid
  password: change-me
---
apiVersion: apps/v1
kind: Deployment
metadata: { name: cosid-server, namespace: cosid }
spec:
  replicas: 2
  selector: { matchLabels: { app: cosid-server } }
  template:
    metadata: { labels: { app: cosid-server } }
    spec:
      terminationGracePeriodSeconds: 30
      containers:
        - name: cosid-server
          image: ahoowang/cosid-server:__COSID_VERSION__
          ports: [ { name: http, containerPort: 8688 } ]
          env:
            - { name: COSID_STORES_MAIN_TYPE, value: jdbc }
            - { name: COSID_STORES_MAIN_TIMEOUT, value: 2s }
            - { name: COSID_STORES_MAIN_OPTIONS_URL, valueFrom: { secretKeyRef: { name: cosid-server-db, key: url } } }
            - { name: COSID_STORES_MAIN_OPTIONS_USERNAME, valueFrom: { secretKeyRef: { name: cosid-server-db, key: username } } }
            - { name: COSID_STORES_MAIN_OPTIONS_PASSWORD, valueFrom: { secretKeyRef: { name: cosid-server-db, key: password } } }
            - { name: COSID_SERVER_AUTH_ALLOW_ANONYMOUS, value: "true" }
            - { name: SERVER_SHUTDOWN, value: graceful }
          # Liveness never asks the store; readiness reserves on it (the contributor cosidServerStore), so a server whose store is down gets no calls.
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 10
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: http }
            periodSeconds: 5
            failureThreshold: 2
          lifecycle:
            preStop: { sleep: { seconds: 5 } }   # let the Service drop the pod before it stops taking calls
          resources:
            requests: { cpu: 250m, memory: 512Mi }
            limits: { memory: 512Mi }
---
apiVersion: v1
kind: Service
metadata: { name: cosid-server, namespace: cosid }
spec:
  type: ClusterIP
  selector: { app: cosid-server }
  ports: [ { name: http, port: 8688, targetPort: http } ]
---
# Only the application namespaces (labelled cosid-client=true) reach the server.
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata: { name: cosid-server, namespace: cosid }
spec:
  podSelector: { matchLabels: { app: cosid-server } }
  policyTypes: [ Ingress ]
  ingress:
    - from: [ { namespaceSelector: { matchLabels: { cosid-client: "true" } } } ]
      ports: [ { port: 8688 } ]
```

Create the tables beforehand from the [schema files](../backends/jdbc.md#schema) (`initSchema` is for development).
The `sleep` pre-stop hook needs Kubernetes 1.30 or later;
on older clusters use `exec: { command: [ sleep, "5" ] }`, which needs a `sleep` binary in the image.

## The application

```yaml
# application.yaml of order-service
spring:
  application:
    name: order-service
  lifecycle:
    timeout-per-shutdown-phase: 20s
server:
  shutdown: graceful
cosid:
  namespace: ${spring.application.name}        # the service's own slots and range keys
  stores:
    remote:
      type: remote
      timeout: 2s
      options:
        baseUrl: http://cosid-server.cosid.svc:8688
  machine:
    leased:
      store: remote
      bits: 6                                  # 64 slots; see "How many slots" below
  machines:
    global:                                    # shared with every service that issues cosid80 tokens
      namespace: global-ids
      leased: { store: remote, bits: 10 }
  generators:
    order:   { strategy: segment, store: remote }
    event:   { strategy: snowflake }           # on the default machine, in namespace order-service
    token:   { strategy: cosid80, machine: global }
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState, cosid       # a pod that lost its slot stops taking traffic
```

`/actuator/health/cosid` is `DOWN` when a machine lost its lease and could not acquire one again: its time-based
generators then fail fast with `SlotLostException`. In the readiness group it takes such a pod out of the Service
until it holds a slot again. Leave it out of liveness: a restart does not bring the store back.

### Ids unique across services

Every service that issues `token` ids declares the same `cosid.machines.global` block: the same `namespace`, store,
`bits`, `tolerance` and `slot-offset`. The first runtime to use `global-ids` records them, and a runtime with other
values fails at start. Segments and the default machine stay in each service's own namespace, so the services do not
share range keys or slots there. Clients that issue few ids, or that are not on the JVM, can call cosid-server's `/ids`
instead (`cosid.server.ids.enabled`).

## How many slots

A leased slot stays taken while its pod lives and, after a pod is killed without releasing it, until its lease expires
(`ttl + tolerance`, 31s by default). Size `bits` for the **peak**:

```
slots needed ≥ max replicas (the HPA's maxReplicas, or replicas)
             + maxSurge of a rolling update
             + pods killed within the last ttl + tolerance (crash loops, evictions)
```

Then take the next power of two with room to spare. A Deployment of up to 20 replicas with `maxSurge: 25%` needs 25
slots at a rolling update; `bits: 6` (64) leaves room for crashes. For a shared namespace such as `global-ids`, add up
the peaks of **all** the services in it. `bits` must fit the layout's node bits (10 for `SNOWFLAKE_MS`, 20 for
cosid80). When every slot is taken, a starting pod waits up to `acquire-timeout` (the `ttl`) and then fails its start:
give the startup probe at least that long.

## The Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: order-service, namespace: shop }
spec:
  replicas: 4
  strategy:
    type: RollingUpdate
    rollingUpdate: { maxSurge: 25%, maxUnavailable: 0 }
  selector: { matchLabels: { app: order-service } }
  template:
    metadata: { labels: { app: order-service } }
    spec:
      # preStop (5s) + graceful shutdown phase (20s) + the slot release (one store call, at most its 2s timeout), with room.
      terminationGracePeriodSeconds: 40
      containers:
        - name: order-service
          image: registry.example.com/shop/order-service:1.0.0
          ports: [ { name: http, containerPort: 8080 } ]
          startupProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 5
            failureThreshold: 12                # 60s: past acquire-timeout (30s) while all slots are held
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: http }
            periodSeconds: 5
          lifecycle:
            preStop: { sleep: { seconds: 5 } }
```

The namespace `shop` carries the label `cosid-client: "true"` so that the `NetworkPolicy` above admits it.

## Stopping a pod

On `SIGTERM` the pod goes through these steps, all within `terminationGracePeriodSeconds`:

1. `preStop` sleeps while the endpoint is removed from the Service, so no new request arrives at a closing pod.
2. Spring's graceful shutdown lets the requests in flight finish (`spring.lifecycle.timeout-per-shutdown-phase`).
3. The context closes the `CosIdRuntime`: generators stop, then each machine **releases its slot with the exact last
   issued timestamp**, then the stores close.

A released slot is free at once, and its next holder starts right after the last timestamp. If the grace period runs
out first, Kubernetes sends `SIGKILL`: ids stay unique, but the slot stays taken until its lease expires (up to
`ttl + tolerance`), which is what the "pods killed" term of the slot count is for. Keep the grace period above the sum
of the steps, as in the example.

## StatefulSets

A StatefulSet's pods have stable ordinals. Use one as the `preferred-slot` of a leased machine
(`preferred-slot: ${POD_ORDINAL:}`, with `POD_ORDINAL` from the `apps.kubernetes.io/pod-index` label through the
Downward API): the lease still guards it, and a pod that finds its slot taken gets another one. A
[static slot](../concepts/slot-lease.md#static-slots) (`cosid.machine.static`) skips the store altogether, but then
the StatefulSet alone keeps slots unique, and `state-file` must be on a persistent volume.

## Checklist

- [ ] cosid-server: two or more replicas, `ClusterIP` only; tokens, or `allow-anonymous` with a `NetworkPolicy`.
- [ ] Readiness probes: the server's `/actuator/health/readiness` (asks the store); the application's readiness group
      includes `cosid`.
- [ ] `bits` covers peak replicas + surge + recently killed pods; the same `bits` for every service of a shared namespace.
- [ ] `terminationGracePeriodSeconds` > `preStop` + shutdown phase + store timeout.
- [ ] Startup probe longer than `acquire-timeout`.
- [ ] The rest of the [production checklist](production.md).
