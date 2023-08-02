# 快速上手

## Examples

[CosId-Examples](https://github.com/Ahoo-Wang/CosId/tree/main/examples)

## 安装

> 开发者可以任选一种的分发器（Redis/JDBC/Mongodb/Zookeeper）,并引入对应的依赖。

### Redis 分发器

[CosId-Example-Redis](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-redis)

#### Gradle Kotlin DSL

``` kotlin
    val cosidVersion = "latestVersion";
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("me.ahoo.cosid:cosid-spring-redis:${cosidVersion}")
    implementation("me.ahoo.cosid:cosid-spring-boot-starter:${cosidVersion}")
```

#### Maven

```xml
<?xml version="1.0" encoding="UTF-8"?>

<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    <artifactId>demo</artifactId>
    <properties>
        <cosid.version>latestVersion</cosid.version>
    </properties>

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

</project>
```

#### 应用配置 (`application.yaml`)

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
> TIPS: 默认情况下，开启 `snowflake`/`segment` 会生成一个共享的`IdGenerator` 注册到 `Spring` 容器 以及 `DefaultIdGeneratorProvider.INSTANCE`。

## 使用

> 通过 `@Autowired` 注入 `IdGenerator` 。

```java
    @Autowired
    private IdGenerator idGenerator;
``` 

> 通过 `DefaultIdGeneratorProvider.INSTANCE` 获取共享 `IdGenerator` 。

```java
    DefaultIdGeneratorProvider.INSTANCE.getShare()
```



