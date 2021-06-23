# CosId

Global distributed ID generator

# Installation

### Gradle

> Kotlin DSL

``` kotlin
    val coskyVersion = "0.3.0";
    implementation("me.ahoo.cosid:cosid-core:${coskyVersion}")
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
        <cosid.version>0.3.0</cosid.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-core</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>

</project>
```

## IdGenerator

```java
        IdGenerator idGen=new MillisecondSnowflakeId(1);
        long id=idGen.generate();

        MillisecondSnowflakeIdStateParser snowflakeIdStateParser = MillisecondSnowflakeIdStateParser.of(idGen);
        SnowflakeIdState idState = snowflakeIdStateParser.parse(id);
        idState.getFriendlyId(); //20210623131730192-1-0
        
```

### SafeJavaScriptSnowflakeId

```java
    IdGenerator snowflakeId = SafeJavaScriptSnowflakeId.ofMillisecond(1);
```

## MachineIdDistributor

### StatefulSetMachineIdDistributor (Kubernetes)

```java
int machineId=StatefulSetMachineIdDistributor.INSTANCE.distribute();
```

## JMH-Benchmark

```
Benchmark                                                    Mode  Cnt        Score   Error  Units
SnowflakeIdBenchmark.millisecondSnowflakeId_generate        thrpt       4095857.975          ops/s
SnowflakeIdBenchmark.safeJsMillisecondSnowflakeId_generate  thrpt         63742.923          ops/s
SnowflakeIdBenchmark.safeJsSecondSnowflakeId_generate       thrpt         60520.691          ops/s
SnowflakeIdBenchmark.secondSnowflakeId_generate             thrpt       4210452.122          ops/s

```
