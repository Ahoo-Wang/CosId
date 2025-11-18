# Quick Start

## Installation

:::tip
Developers can choose any distributor (`Redis`/`JDBC`/`Mongodb`/`Zookeeper`) and introduce the corresponding dependencies.
:::

Next, taking `Redis` extension as an example: [CosId-Example-Redis](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-redis)

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("me.ahoo.cosid:cosid-spring-redis:${cosidVersion}")
    implementation("me.ahoo.cosid:cosid-spring-boot-starter:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
            <version>${springboot.version}</version>
        </dependency>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-spring-redis</artifactId>
            <version>${cosid.version}</version>
        </dependency>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-spring-boot-starter</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

## Application Configuration

```yaml
spring:
  data:
    redis:
      host: localhost # Redis distributor directly depends on spring-data-redis, which can save additional configuration.
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # Optional, when using snowflake algorithm, set to true
    distributor:
      type: redis
  snowflake:
    enabled: true # Optional, when using snowflake algorithm, set to true
  segment:
    enabled: true # Optional, when using segment algorithm, set to true
    distributor:
      type: redis
```

:::tip
By default, enabling `snowflake`/`segment` will generate shared (`__share__`) `IdGenerator` registered to `Spring` container and `DefaultIdGeneratorProvider.INSTANCE`.
:::

:::warning
When enabling both `snowflake`/`segment` at the same time, only one shared (`__share__`) `IdGenerator` will be injected into the `Spring` container (name conflict), the other will be ignored.
:::

`IdGenerator` `Bean Name` rules:
- SegmentId: `[name]SegmentId` , e.g.: `__share__SegmentId`
- SnowflakeId: `[name]SnowflakeId`, e.g.: `__share__SnowflakeId`

## Usage

> Inject `IdGenerator` via `@Autowired`.

```java {1,6}
    @Qualifier("__share__SegmentId")
    @Lazy
    @Autowired
    private SegmentId segmentId;

    @Qualifier("__share__SnowflakeId")
    @Lazy
    @Autowired
    private SnowflakeId snowflakeId;
```

> Get shared `IdGenerator` via `DefaultIdGeneratorProvider.INSTANCE`.

```java
    DefaultIdGeneratorProvider.INSTANCE.getShare();
```

### Register Multiple ID Generators

> Register multiple ID generators via configuration file `provider`

```yaml
spring:
  data:
    redis:
      host: localhost # Redis distributor directly depends on spring-data-redis, which can save additional configuration.
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: false # Optional, when using snowflake algorithm, set to true
    distributor:
      type: redis
  segment:
    enabled: true # Optional, when using segment algorithm, set to true
    distributor:
      type: redis
    provider:
      order: # ID generator named order
        offset: 10000
      item: # ID generator named item
        offset: 10000
```

> Register multiple ID generators via code

```java
	@Resource
  	IdGeneratorProvider provider;
	@Resource
    IdSegmentDistributorFactory idSegmentDistributorFactory;

	public void registerIdGenerator(String idName) {
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition("namespace", "idName",0,100);
        IdSegmentDistributor idSegmentDistributor = idSegmentDistributorFactory.create(definition);
        SegmentChainId segmentId = new SegmentChainId(idSegmentDistributor);
        provider.set(idName, segmentId);
    }
```

## Examples

Developers can quickly start the `CosId` journey by learning from [CosId-Examples](https://github.com/Ahoo-Wang/CosId/tree/main/examples).
