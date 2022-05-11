# 快速上手

## Examples

[CosId-Examples](https://github.com/Ahoo-Wang/CosId/tree/main/examples)

## 安装

### Gradle

> Kotlin DSL

``` kotlin
    val cosidVersion = "1.8.6";
    implementation("me.ahoo.cosid:cosid-spring-boot-starter:${cosidVersion}")
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
        <cosid.version>1.8.6</cosid.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-spring-boot-starter</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>

</project>
```

### application.yaml

```yaml
cosid:
  namespace: ${spring.application.name}
  snowflake:
    enabled: true
    machine:
      distributor:
        type: redis
```

[//]: # ()
[//]: # (*[CosId-SnowflakeId]&#40;https://github.com/Ahoo-Wang/CosId/tree/main/cosid-core/src/main/java/me/ahoo/cosid/snowflake&#41;*)

[//]: # (主要解决 *SnowflakeId* 俩大问题：机器号分配问题、时钟回拨问题。 并且提供更加友好、灵活的使用体验。)

[//]: # ()
[//]: # (### MachineIdDistributor &#40;MachineId 分配器&#41;)

[//]: # ()
[//]: # (> 目前 *[CosId]&#40;https://github.com/Ahoo-Wang/CosId&#41;* 提供了以下三种 `MachineId` 分配器。)

[//]: # ()
[//]: # (#### ManualMachineIdDistributor)

[//]: # ()
[//]: # (```yaml)

[//]: # (cosid:)

[//]: # (  snowflake:)

[//]: # (    machine:)

[//]: # (      distributor:)

[//]: # (        type: manual)

[//]: # (        manual:)

[//]: # (          machine-id: 0)

[//]: # (```)

[//]: # ()
[//]: # (> 手动分配 `MachineId`。)

[//]: # ()
[//]: # (#### StatefulSetMachineIdDistributor)

[//]: # ()
[//]: # (```yaml)

[//]: # (cosid:)

[//]: # (  snowflake:)

[//]: # (    machine:)

[//]: # (      distributor:)

[//]: # (        type: stateful_set)

[//]: # (```)

[//]: # ()
[//]: # (> 使用 `Kubernetes` 的 `StatefulSet` 提供的稳定的标识 ID 作为机器号。)

[//]: # ()
[//]: # (#### RedisMachineIdDistributor)

[//]: # ()
[//]: # (![RedisMachineIdDistributor]&#40;../docs/RedisMachineIdDistributor.png&#41;)

[//]: # ()
[//]: # (```yaml)

[//]: # (cosid:)

[//]: # (  snowflake:)

[//]: # (    machine:)

[//]: # (      distributor:)

[//]: # (        type: redis)

[//]: # (```)

[//]: # ()
[//]: # (> 使用 `Redis` 作为机器号的分发存储。)

[//]: # ()
[//]: # (### ClockBackwardsSynchronizer &#40;时钟回拨同步器&#41;)

[//]: # ()
[//]: # (```yaml)

[//]: # (cosid:)

[//]: # (  snowflake:)

[//]: # (    clock-backwards:)

[//]: # (      spin-threshold: 10)

[//]: # (      broken-threshold: 2000)

[//]: # (```)

[//]: # ()
[//]: # (默认提供的 `DefaultClockBackwardsSynchronizer` 时钟回拨同步器使用主动等待同步策略，`spinThreshold`&#40;默认值 10 毫秒&#41; 用于设置自旋等待阈值， 当大于`spinThreshold`)

[//]: # (时使用线程休眠等待时钟同步，如果超过`brokenThreshold`&#40;默认值 2 秒&#41;时会直接抛出`ClockTooManyBackwardsException`异常。)

[//]: # ()
[//]: # (### MachineStateStorage &#40;机器状态存储&#41;)

[//]: # ()
[//]: # (```java)

[//]: # (public class MachineState {)

[//]: # (    public static final MachineState NOT_FOUND = of&#40;-1, -1&#41;;)

[//]: # (    private final int machineId;)

[//]: # (    private final long lastTimeStamp;)

[//]: # ()
[//]: # (    public MachineState&#40;int machineId, long lastTimeStamp&#41; {)

[//]: # (        this.machineId = machineId;)

[//]: # (        this.lastTimeStamp = lastTimeStamp;)

[//]: # (    })

[//]: # ()
[//]: # (    public int getMachineId&#40;&#41; {)

[//]: # (        return machineId;)

[//]: # (    })

[//]: # ()
[//]: # (    public long getLastTimeStamp&#40;&#41; {)

[//]: # (        return lastTimeStamp;)

[//]: # (    })

[//]: # ()
[//]: # (    public static MachineState of&#40;int machineId, long lastStamp&#41; {)

[//]: # (        return new MachineState&#40;machineId, lastStamp&#41;;)

[//]: # (    })

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (```yaml)

[//]: # (cosid:)

[//]: # (  snowflake:)

[//]: # (    machine:)

[//]: # (      state-storage:)

[//]: # (        local:)

[//]: # (          state-location: ./cosid-machine-state/)

[//]: # (```)

[//]: # ()
[//]: # (默认提供的 `LocalMachineStateStorage` 本地机器状态存储，使用本地文件存储机器号、最近一次时间戳，用作 `MachineState` 缓存。)

[//]: # ()
[//]: # (### ClockSyncSnowflakeId &#40;主动时钟同步 `SnowflakeId`&#41;)

[//]: # ()
[//]: # (```yaml)

[//]: # (cosid:)

[//]: # (  snowflake:)

[//]: # (    share:)

[//]: # (      clock-sync: true)

[//]: # (```)

[//]: # ()
[//]: # (默认 `SnowflakeId` 当发生时钟回拨时会直接抛出 `ClockBackwardsException` 异常，而使用 `ClockSyncSnowflakeId` 会使用 `ClockBackwardsSynchronizer`)

[//]: # (主动等待时钟同步来重新生成 ID，提供更加友好的使用体验。)

[//]: # ()
[//]: # (### SafeJavaScriptSnowflakeId &#40;`JavaScript` 安全的 `SnowflakeId`&#41;)

[//]: # ()
[//]: # (```java)

[//]: # (SnowflakeId snowflakeId=SafeJavaScriptSnowflakeId.ofMillisecond&#40;1&#41;;)

[//]: # (```)

[//]: # ()
[//]: # (`JavaScript` 的 `Number.MAX_SAFE_INTEGER` 只有 53 位，如果直接将 63 位的 `SnowflakeId` 返回给前端，那么会值溢出的情况，通常我们可以将`SnowflakeId`)

[//]: # (转换为 `String` 类型或者自定义 `SnowflakeId` 位分配来缩短 `SnowflakeId` 的位数 使 `ID` 提供给前端时不溢出。)

[//]: # ()
[//]: # (### SnowflakeFriendlyId &#40;可以将 `SnowflakeId` 解析成可读性更好的 `SnowflakeIdState` &#41;)

[//]: # ()
[//]: # (```yaml)

[//]: # (cosid:)

[//]: # (  snowflake:)

[//]: # (    share:)

[//]: # (      friendly: true)

[//]: # (```)

[//]: # ()
[//]: # (```java)

[//]: # (public class SnowflakeIdState {)

[//]: # ()
[//]: # (    private final long id;)

[//]: # ()
[//]: # (    private final int machineId;)

[//]: # ()
[//]: # (    private final long sequence;)

[//]: # ()
[//]: # (    private final LocalDateTime timestamp;)

[//]: # (    /**)

[//]: # (     * {@link #timestamp}-{@link #machineId}-{@link #sequence})

[//]: # (     */)

[//]: # (    private final String friendlyId;)

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (```java)

[//]: # (public interface SnowflakeFriendlyId extends SnowflakeId {)

[//]: # ()
[//]: # (    SnowflakeIdState friendlyId&#40;long id&#41;;)

[//]: # ()
[//]: # (    SnowflakeIdState ofFriendlyId&#40;String friendlyId&#41;;)

[//]: # ()
[//]: # (    default SnowflakeIdState friendlyId&#40;&#41; {)

[//]: # (        long id = generate&#40;&#41;;)

[//]: # (        return friendlyId&#40;id&#41;;)

[//]: # (    })

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (```java)

[//]: # (        SnowflakeFriendlyId snowflakeFriendlyId=new DefaultSnowflakeFriendlyId&#40;snowflakeId&#41;;)

[//]: # (        SnowflakeIdState idState=snowflakeFriendlyId.friendlyId&#40;&#41;;)

[//]: # (        idState.getFriendlyId&#40;&#41;; //20210623131730192-1-0)

[//]: # (```)

[//]: # ()
[//]: # (## SegmentId &#40;号段模式&#41;)

[//]: # ()
[//]: # ([//]: # &#40;![SegmentId]&#40;../docs/SegmentId.png&#41;&#41;)
[//]: # ()
[//]: # (### RedisIdSegmentDistributor &#40;使用`Redis`作为号段分发后端存储&#41;)

[//]: # ()
[//]: # (```yaml)

[//]: # (cosid:)

[//]: # (  segment:)

[//]: # (    enabled: true)

[//]: # (    distributor:)

[//]: # (      type: redis)

[//]: # (```)

[//]: # ()
[//]: # (### JdbcIdSegmentDistributor &#40;使用关系型数据库`Db`作为号段分发后端存储&#41;)

[//]: # ()
[//]: # (> 初始化 `cosid` table)

[//]: # ()
[//]: # (```mysql)

[//]: # (create table if not exists cosid)

[//]: # (&#40;)

[//]: # (    name            varchar&#40;100&#41; not null comment '{namespace}.{name}',)

[//]: # (    last_max_id     bigint       not null default 0,)

[//]: # (    last_fetch_time bigint       not null,)

[//]: # (    constraint cosid_pk)

[//]: # (        primary key &#40;name&#41;)

[//]: # (&#41; engine = InnoDB;)

[//]: # ()
[//]: # (```)

[//]: # ()
[//]: # (```yaml)

[//]: # (spring:)

[//]: # (  datasource:)

[//]: # (    url: jdbc:mysql://localhost:3306/test_db)

[//]: # (    username: root)

[//]: # (    password: root)

[//]: # (cosid:)

[//]: # (  segment:)

[//]: # (    enabled: true)

[//]: # (    distributor:)

[//]: # (      type: jdbc)

[//]: # (      jdbc:)

[//]: # (        enable-auto-init-cosid-table: false)

[//]: # (        enable-auto-init-id-segment: true)

[//]: # (```)

[//]: # ()
[//]: # (开启 `enable-auto-init-id-segment:true` 之后，应用启动时会尝试创建 `idSegment` 记录，避免手动创建。类似执行了以下初始化sql脚本，不用担心误操作，因为 `name` 是主键。)

[//]: # ()
[//]: # (```mysql)

[//]: # (insert into cosid)

[//]: # (    &#40;name, last_max_id, last_fetch_time&#41;)

[//]: # (    value)

[//]: # (    &#40;'namespace.name', 0, unix_timestamp&#40;&#41;&#41;;)

[//]: # (```)

[//]: # ()
[//]: # (### SegmentChainId &#40;号段链模式&#41;)

[//]: # ()
[//]: # (![SegmentChainId]&#40;../docs/SegmentChainId.png&#41;)

[//]: # ()
[//]: # (```yaml)

[//]: # (cosid:)

[//]: # (  segment:)

[//]: # (    enabled: true)

[//]: # (    mode: chain)

[//]: # (    chain:)

[//]: # (      safe-distance: 5)

[//]: # (      prefetch-worker:)

[//]: # (        core-pool-size: 2)

[//]: # (        prefetch-period: 1s)

[//]: # (```)

[//]: # ()
[//]: # (## IdGeneratorProvider)

[//]: # ()
[//]: # (```yaml)

[//]: # (cosid:)

[//]: # (  snowflake:)

[//]: # (    provider:)

[//]: # (      bizA:)

[//]: # (        #      epoch:)

[//]: # (        #      timestamp-bit:)

[//]: # (        sequence-bit: 12)

[//]: # (      bizB:)

[//]: # (        #      epoch:)

[//]: # (        #      timestamp-bit:)

[//]: # (        sequence-bit: 12)

[//]: # (```)

[//]: # ()
[//]: # (```java)

[//]: # (IdGenerator idGenerator=idGeneratorProvider.get&#40;"bizA"&#41;;)

[//]: # (```)

[//]: # ()
[//]: # (在实际使用中我们一般不会所有业务服务使用同一个`IdGenerator`而是不同的业务使用不同的`IdGenerator`，那么`IdGeneratorProvider`就是为了解决这个问题而存在的，他是 `IdGenerator`)

[//]: # (的容器，可以通过业务名来获取相应的`IdGenerator`。)

[//]: # ()
[//]: # (### CosIdPlugin（MyBatis 插件）)

[//]: # ()
[//]: # (> Kotlin DSL)

[//]: # ()
[//]: # (``` kotlin)

[//]: # (    implementation&#40;"me.ahoo.cosid:cosid-mybatis:${cosidVersion}"&#41;)

[//]: # (```)

[//]: # ()
[//]: # (```java)

[//]: # ()
[//]: # (@Target&#40;{ElementType.FIELD}&#41;)

[//]: # (@Documented)

[//]: # (@Retention&#40;RetentionPolicy.RUNTIME&#41;)

[//]: # (public @interface CosId {)

[//]: # (    String value&#40;&#41; default IdGeneratorProvider.SHARE;)

[//]: # ()
[//]: # (    boolean friendlyId&#40;&#41; default false;)

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (```java)

[//]: # (public class LongIdEntity {)

[//]: # ()
[//]: # (    @CosId&#40;value = "safeJs"&#41;)

[//]: # (    private Long id;)

[//]: # ()
[//]: # (    public Long getId&#40;&#41; {)

[//]: # (        return id;)

[//]: # (    })

[//]: # ()
[//]: # (    public void setId&#40;Long id&#41; {)

[//]: # (        this.id = id;)

[//]: # (    })

[//]: # (})

[//]: # ()
[//]: # (public class FriendlyIdEntity {)

[//]: # ()
[//]: # (    @CosId&#40;friendlyId = true&#41;)

[//]: # (    private String id;)

[//]: # ()
[//]: # (    public String getId&#40;&#41; {)

[//]: # (        return id;)

[//]: # (    })

[//]: # ()
[//]: # (    public void setId&#40;String id&#41; {)

[//]: # (        this.id = id;)

[//]: # (    })

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (```java)

[//]: # ()
[//]: # (@Mapper)

[//]: # (public interface OrderRepository {)

[//]: # (    @Insert&#40;"insert into t_table &#40;id&#41; value &#40;#{id}&#41;;"&#41;)

[//]: # (    void insert&#40;LongIdEntity order&#41;;)

[//]: # ()
[//]: # (    @Insert&#40;{)

[//]: # (            "<script>",)

[//]: # (            "insert into t_friendly_table &#40;id&#41;",)

[//]: # (            "VALUES" +)

[//]: # (                    "<foreach item='item' collection='list' open='' separator=',' close=''>" +)

[//]: # (                    "&#40;#{item.id}&#41;" +)

[//]: # (                    "</foreach>",)

[//]: # (            "</script>"}&#41;)

[//]: # (    void insertList&#40;List<FriendlyIdEntity> list&#41;;)

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (```java)

[//]: # (        LongIdEntity entity=new LongIdEntity&#40;&#41;;)

[//]: # (        entityRepository.insert&#40;entity&#41;;)

[//]: # (        /**)

[//]: # (         * {)

[//]: # (         *   "id": 208796080181248)

[//]: # (         * })

[//]: # (         */)

[//]: # (        return entity;)

[//]: # (```)

[//]: # ()
[//]: # ()
