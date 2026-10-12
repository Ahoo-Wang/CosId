# IdGeneratorProvider

> `IdGenerator` container

<p align="center">
  <img src="../../../public/assets/design/IdGeneratorProvider-impl-class.png" alt="IdGeneratorProvider implementation class diagram"/>
</p>

## DefaultIdGeneratorProvider

`DefaultIdGeneratorProvider` is the default `IdGenerator` container implementation. It is a thread-safe registry that manages named ID generator instances using a `ConcurrentHashMap`.

### Key Features

- **Thread-safe**: Uses `ConcurrentHashMap` for concurrent access
- **Singleton**: Shared instance available via `INSTANCE`
- **Named generators**: Supports multiple named ID generators

### Usage

#### Get Shared Generator

```java
IdGenerator share = DefaultIdGeneratorProvider.INSTANCE.getShare();
long id = share.generate();
```

#### Get Named Generator

```java
Optional<IdGenerator> orderGenerator = DefaultIdGeneratorProvider.INSTANCE.get("order", IdGenerator.class);
orderGenerator.ifPresent(gen -> {
    long id = gen.generate();
});
```

#### Register Generator

```java
DefaultIdGeneratorProvider.INSTANCE.set("order", segmentChainId);
```

#### Remove Generator

```java
StringIdGenerator removed = DefaultIdGeneratorProvider.INSTANCE.remove("order");
```

## Spring Boot

In Spring Boot applications, inject the `IdGeneratorProvider` bean instead of using `DefaultIdGeneratorProvider.INSTANCE` directly. The annotation accessors and the MyBatis, Spring Data JDBC, Activiti, Flowable and Axon adapters all resolve generators from this bean, so a custom `IdGeneratorProvider` bean takes effect for all of them.

| Property | Default | Description |
|----------|---------|-------------|
| `cosid.provider.isolated` | `false` | `false`: the `IdGeneratorProvider` bean is `DefaultIdGeneratorProvider.INSTANCE` (same as previous releases). `true`: each `ApplicationContext` gets its own `DefaultIdGeneratorProvider`, cleared when the context closes; generators are not shared between contexts (e.g. devtools restarts, cached test contexts) and `INSTANCE` no longer holds Spring-registered generators. |

## LazyIdGenerator

`LazyIdGenerator` looks a generator up by name in the provider on first access, so it can be created before the generator is registered.

### Usage

```java
LazyIdGenerator lazyGenerator = new LazyIdGenerator("order");
long id = lazyGenerator.generate();
```

### Use Cases

- Fields or adapters created before the generator is registered
- A generator that may be replaced at runtime: each access looks it up again until it is found
