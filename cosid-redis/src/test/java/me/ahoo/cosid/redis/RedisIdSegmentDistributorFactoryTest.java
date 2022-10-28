package me.ahoo.cosid.redis;

import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_OFFSET;
import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_STEP;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosky.core.redis.RedisConfig;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;

import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * @author : Rocher Kong
 */
class RedisIdSegmentDistributorFactoryTest {
    protected RedisClient redisClient;
    protected RedisIdSegmentDistributorFactory redisIdSegmentDistributorFactory;
    protected RedisConfig redisConfig;
    protected IdSegmentDistributorDefinition idSegmentDistributorDefinition;
    
    @BeforeEach
    void setup() {
        System.out.println("--- initRedis ---");
        String redisUrl = "redis://localhost:6379";
        redisClient = RedisClient.create(redisUrl);
        redisConfig = new RedisConfig();
        redisConfig.setUrl(redisUrl);
        redisConfig.setMode(RedisConfig.RedisMode.STANDALONE);
        
        redisIdSegmentDistributorFactory = new RedisIdSegmentDistributorFactory(new RedisConnectionFactory(redisClient.getResources(), redisConfig), Duration.ofSeconds(1));
        idSegmentDistributorDefinition = new IdSegmentDistributorDefinition("RedisIdSegmentDistributorFactoryTest", MockIdGenerator.INSTANCE.generateAsString(), DEFAULT_OFFSET, DEFAULT_STEP);
    }
    
    @Test
    void create() {
        IdSegmentDistributor idSegmentDistributor = redisIdSegmentDistributorFactory.create(idSegmentDistributorDefinition);
        Assertions.assertNotNull(idSegmentDistributor);
        IdSegment nextId = idSegmentDistributor.nextIdSegment();
        assertThat(nextId.getMaxId(), equalTo(DEFAULT_STEP));
    }
}
