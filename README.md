# CosId

Global distributed ID generator

# Installation

[CosId-Examples](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-example)

### Gradle

> Kotlin DSL

``` kotlin
    val cosidVersion = "0.8.0";
    implementation("me.ahoo.cosid:spring-boot-starter-cosid:${cosidVersion}")
```

### Maven

```xml
<?xml version="1.0" encoding="UTF-8"?>

<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    <artifactId>demo</artifactId>
    <properties>
        <cosid.version>0.8.0</cosid.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>spring-boot-starter-cosid</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>

</project>
```

### application.yaml

```yaml
cosid:
  #  stateful-set:
  #    enabled: true
  #  manual:
  #    enabled: true
  #    machine-id: 1
  redis:
    enabled: true
  providers:
    order:
      #      epoch:
      #      timestamp-bit:
      #      machine-bit:
      sequence-bit: 12
    user:
      #      epoch:
      #      timestamp-bit:
      #      machine-bit:
      sequence-bit: 12
```

## IdGenerator

```java
        IdGenerator idGen=new MillisecondSnowflakeId(1);
        long id=idGen.generate();

        MillisecondSnowflakeIdStateParser snowflakeIdStateParser=MillisecondSnowflakeIdStateParser.of(idGen);
        SnowflakeIdState idState=snowflakeIdStateParser.parse(id);
        idState.getFriendlyId(); //20210623131730192-1-0

```

### SafeJavaScriptSnowflakeId

```java
    IdGenerator snowflakeId=SafeJavaScriptSnowflakeId.ofMillisecond(1);
```

## MachineIdDistributor

### StatefulSetMachineIdDistributor (On Kubernetes)

```yaml
cosid:
  stateful-set:
    enabled: true
```

### ManualMachineIdDistributor

```yaml
cosid:
  manual:
    enabled: true
    machine-id: 1
```

### RedisMachineIdDistributor

> Support clock callback verification, and wait until it catches up with the clock callback.

``` kotlin
    val cosidVersion = "0.8.0";
    implementation("me.ahoo.cosid:cosid-redis:${cosidVersion}")
```

```yaml
cosid:
  redis:
    enabled: true
```

## JMH-Benchmark

### SnowflakeId

```
Benchmark                                                    Mode  Cnt        Score   Error  Units
SnowflakeIdBenchmark.millisecondSnowflakeId_generate        thrpt       4093924.313          ops/s
SnowflakeIdBenchmark.safeJsMillisecondSnowflakeId_generate  thrpt        511542.292          ops/s
SnowflakeIdBenchmark.safeJsSecondSnowflakeId_generate       thrpt        511939.629          ops/s
SnowflakeIdBenchmark.secondSnowflakeId_generate             thrpt       4204761.870          ops/s
```


### RedisIdGenerator

```
Benchmark                              Mode  Cnt         Score   Error  Units
RedisIdGeneratorBenchmark.step_1      thrpt          86243.935          ops/s
RedisIdGeneratorBenchmark.step_100    thrpt        1718229.010          ops/s
RedisIdGeneratorBenchmark.step_1000   thrpt       12688174.755          ops/s
RedisIdGeneratorBenchmark.step_10000  thrpt       13995195.387          ops/s
```
