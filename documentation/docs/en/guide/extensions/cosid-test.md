# Compatibility Test Suite

The compatibility test suite is a set of test cases used to verify whether specific interface implementations comply with specifications.

Through the _cosid-test module_, convenient and correctness guarantees are provided for custom extensions.
This standardized verification method not only simplifies extension development, reduces potential error risks, but also ensures the consistency and stability of the entire ecosystem.


## Installation

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

## Redis Extension Example

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