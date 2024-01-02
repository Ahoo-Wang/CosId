# CosId-Jdbc 模块

[cosid-jdbc](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-jdbc) 提供 **关系型数据库** 的支持。实现了：

- `MachineIdDistributor`：作为**雪花算法**(`SnowflakeId`)的机器号分配器 (`MachineIdDistributor`)。
- `IdSegmentDistributor`：作为**号段算法**(`SegmentId`)的号段分发器 (`IdSegmentDistributor`)。

## 安装

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("me.ahoo.cosid:cosid-jdbc:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jdbc</artifactId>
            <version>${springboot.version}</version>
        </dependency>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-jdbc</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

### 创建 `cosid` 表

`cosid` 表作为号段分发器的号段分发记录表。

```sql
create table if not exists cosid
(
    name            varchar(100) not null comment '{namespace}.{name}',
    last_max_id     bigint       not null default 0,
    last_fetch_time bigint       not null,
    constraint cosid_pk
        primary key (name)
) engine = InnoDB;
```

### 创建 `cosid_machine` 表

```sql
create table if not exists cosid_machine
(
    name            varchar(100) not null comment '{namespace}.{machine_id}',
    namespace       varchar(100) not null,
    machine_id      integer      not null default 0,
    last_timestamp  bigint       not null default 0,
    instance_id     varchar(100) not null default '',
    distribute_time bigint       not null default 0,
    revert_time     bigint       not null default 0,
    constraint cosid_machine_pk
        primary key (name)
) engine = InnoDB;

create index if not exists idx_namespace on cosid_machine (namespace);
create index if not exists idx_instance_id on cosid_machine (instance_id);
```

## 配置案例

```yaml {4,10,14}
spring:
  datasource:
    url:  # Jdbc 分发器直接依赖 DataSource
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # 可选，当需要使用雪花算法时，需要设置为 true
    distributor:
      type: jdbc
  segment:
    enabled: true # 可选，当需要使用号段算法时，需要设置为 true
    distributor:
      type: jdbc
```