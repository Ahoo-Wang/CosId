package me.ahoo.cosid.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.util.MockIdGenerator;
import me.ahoo.cosky.core.redis.RedisConfig;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;

import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_OFFSET;
import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_STEP;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author : Rocher Kong
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisIdSegmentDistributorFactoryTest {
    protected RedisClient redisClient;
    protected RedisIdSegmentDistributorFactory redisIdSegmentDistributorFactory;
    protected RedisConfig redisConfig;
    protected IdSegmentDistributorDefinition idSegmentDistributorDefinition;

    @BeforeAll
    private void initRedis() {
        System.out.println("--- initRedis ---");
        String redisUrl="redis://localhost:6379";
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
        IdSegment nextId=idSegmentDistributor.nextIdSegment();
        Assertions.assertTrue(nextId.getMaxId()==100);
    }
}
