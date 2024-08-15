# 快速上手

## 安装

:::tip
开发者可以任选一种的分发器（`Redis`/`JDBC`/`Mongodb`/`Zookeeper`）,并引入对应的依赖。
:::

接下来以 `Redis` 扩展为例： [CosId-Example-Redis](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-redis)

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

## 应用配置

```yaml
spring:
  data:
    redis:
      host: localhost # Redis 分发器直接依赖 spring-data-redis，这样可以省去额外的配置。
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # 可选，当需要使用雪花算法时，需要设置为 true
    distributor:
      type: redis
  snowflake:
    enabled: true # 可选，当需要使用雪花算法时，需要设置为 true
  segment:
    enabled: true # 可选，当需要使用号段算法时，需要设置为 true
    distributor:
      type: redis
```

:::tip
默认情况下，开启 `snowflake`/`segment` 会生成共享的(`__share__`) `IdGenerator` 注册到 `Spring` 容器 以及 `DefaultIdGeneratorProvider.INSTANCE`。
:::

:::warning
当同时开启 `snowflake`/`segment` 时，只有其中一个共享的(`__share__`) `IdGenerator` 会注入到 `Spring` 容器(名称冲突)，另一个会被忽略。
:::

`IdGenerator` `Bean Name` 规则：
- SegmentId: `[name]SegmentId` , 比如 : `__share__SegmentId`
- SnowflakeId: `[name]SnowflakeId`， 比如 : `__share__SnowflakeId`

## 使用

> 通过 `@Autowired` 注入 `IdGenerator` 。

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

> 通过 `DefaultIdGeneratorProvider.INSTANCE` 获取共享 `IdGenerator` 。

```java
    DefaultIdGeneratorProvider.INSTANCE.getShare();
```

> 通过`yml`文件配置`provider`创建多个`生成器`

```yaml
spring:
  data:
    redis:
      host: localhost # Redis 分发器直接依赖 spring-data-redis，这样可以省去额外的配置。
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: false # 可选，当需要使用雪花算法时，需要设置为 true
    distributor:
      type: redis
  segment:
    enabled: true # 可选，当需要使用号段算法时，需要设置为 true
    distributor:
      type: redis
    provider:
      order:
        offset: 10000
      item:
        offset: 10000
```

>通过`代码`创建多个多个`生成器`

```java
	@Resource
  	IdGeneratorProvider provider;
	@Resource
    IdSegmentDistributorFactory idSegmentDistributorFactory;

	public void createIdGenerator(String idName) {
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition("namespace", "idName",0,100);
        IdSegmentDistributor idSegmentDistributor = idSegmentDistributorFactory.create(definition);
        SegmentChainId segmentId = new SegmentChainId(idSegmentDistributor);
        provider.set(idName, segmentId);
    }
```

## Examples

开发者可以通过 [CosId-Examples](https://github.com/Ahoo-Wang/CosId/tree/main/examples) 的学习快速开启 `CosId` 之旅。
