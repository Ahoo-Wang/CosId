# CosId SegmentChainId 手动集成（Redis + Radix62 + BIZ- 前缀）

## 1. 添加依赖

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-bom</artifactId>
            <version>${cosid.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>me.ahoo.cosid</groupId>
    <artifactId>cosid-core</artifactId>
</dependency>
<dependency>
    <groupId>me.ahoo.cosid</groupId>
    <artifactId>cosid-spring-redis</artifactId>
</dependency>
```

## 2. 创建 SegmentChainId

```java
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributor;

// 1. 创建 Redis Distributor
IdSegmentDistributor distributor = new SpringRedisIdSegmentDistributor(
    "my-namespace",   // namespace
    "order_no",       // name
    IdSegmentDistributor.DEFAULT_OFFSET,
    IdSegmentDistributor.DEFAULT_STEP,
    redisTemplate
);

// 2. 创建预取工作线程池
PrefetchWorkerExecutorService prefetchWorker = new PrefetchWorkerExecutorService();

// 3. 创建 SegmentChainId（推荐使用 4 参数构造函数）
SegmentChainId segmentChainId = new SegmentChainId(
    IdSegmentDistributor.DEFAULT_OFFSET,
    IdSegmentDistributor.DEFAULT_STEP,
    distributor,
    prefetchWorker
);
```

也可以使用简化的单参数构造函数（内部自动创建 PrefetchWorker）：

```java
SegmentChainId segmentChainId = new SegmentChainId(distributor);
```

## 3. 配置 Radix62 + BIZ- 前缀的字符串转换

```java
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.segment.StringSegmentId;

// 创建带前缀的转换器
PrefixIdConverter converter = new PrefixIdConverter(
    "BIZ-",
    Radix62IdConverter.PAD_START
);

// 包装为字符串 ID 生成器
StringSegmentId stringId = new StringSegmentId(segmentChainId, converter);

String id = stringId.generateAsString();
// "BIZ-0000001ly7VK"
```

## 4. 生成 ID

```java
// 数字 ID
long id = segmentChainId.generate();

// 字符串 ID（带 BIZ- 前缀）
String strId = stringId.generateAsString();
```

## 5. 生命周期管理

应用关闭时需要释放资源：

```java
// 关闭预取工作线程池
prefetchWorker.shutdown();
```

## 6. 性能说明

SegmentChainId 采用无锁链表 + 异步预取机制，性能约 127M+ ops/s（单线程）。通过调整 `step`（每次分配的 ID 段大小）和 `safeDistance`（预取触发距离）可以进一步优化性能。
