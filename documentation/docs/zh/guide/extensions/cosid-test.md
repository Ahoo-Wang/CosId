# 兼容性测试套件

兼容性测试套件是一组用于验证特定接口实现是否符合规范的测试用例。

通过 _cosid-test 模块_，为自定义扩展提供了便捷和正确性保障。
这种标准化验证方式不仅简化了扩展开发，降低了潜在错误风险，还确保了整个生态系统的一致性和稳定性。


## 安装

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    testImplementation("me.ahoo.cosid:cosid-test:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-test</artifactId>
            <version>${cosid.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
```
:::

## Redis 扩展案例

### MachineIdDistributor

```java
class SpringRedisMachineIdDistributorTest extends MachineIdDistributorSpec {
    StringRedisTemplate stringRedisTemplate;
    
    @BeforeEach
    void setup() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(redisStandaloneConfiguration);
        lettuceConnectionFactory.afterPropertiesSet();
        stringRedisTemplate = new StringRedisTemplate(lettuceConnectionFactory);
    }
    
    @Override
    protected MachineIdDistributor getDistributor() {
        return new SpringRedisMachineIdDistributor(stringRedisTemplate, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT);
    }
    
}
```

### IdSegmentDistributor

```java
class SpringRedisIdSegmentDistributorTest extends IdSegmentDistributorSpec {
    StringRedisTemplate stringRedisTemplate;
    SpringRedisIdSegmentDistributorFactory distributorFactory;
    protected IdSegmentDistributorDefinition idSegmentDistributorDefinition;
    
    @BeforeEach
    void setup() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(redisStandaloneConfiguration);
        lettuceConnectionFactory.afterPropertiesSet();
        stringRedisTemplate = new StringRedisTemplate(lettuceConnectionFactory);
        distributorFactory = new SpringRedisIdSegmentDistributorFactory(stringRedisTemplate);
        idSegmentDistributorDefinition = new IdSegmentDistributorDefinition("SpringRedisIdSegmentDistributorTest", MockIdGenerator.INSTANCE.generateAsString(), 0, 100);
    }
    
    
    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return distributorFactory;
    }
    
    @Override
    protected <T extends IdSegmentDistributor> void setMaxIdBack(T distributor, long maxId) {
        String adderKey = ((SpringRedisIdSegmentDistributor) distributor).getAdderKey();
        stringRedisTemplate.opsForValue().set(adderKey, String.valueOf(maxId - 1));
    }
}
```