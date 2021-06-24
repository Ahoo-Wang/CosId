package me.ahoo.cosid.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisIdGeneratorTest {
    protected RedisClient redisClient;
    protected StatefulRedisConnection<String, String> redisConnection;
    protected RedisIdGenerator redisIdGenerator;

    @BeforeAll
    private void initRedis() {
        System.out.println("--- initRedis ---");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        redisIdGenerator = new RedisIdGenerator("test", "1", 2, redisClient.connect().async());
    }

    @Test
    public void generateOnce() {
        long id = redisIdGenerator.generate();
        Assertions.assertTrue(id > 0);
    }

    @Test
    public void generate() {
        long id = redisIdGenerator.generate();
        Assertions.assertTrue(id > 0);

        long id2 = redisIdGenerator.generate();
        Assertions.assertTrue(id2 > 0);
        Assertions.assertTrue(id2 > id);

        long id3 = redisIdGenerator.generate();
        Assertions.assertTrue(id3 > 0);
        Assertions.assertTrue(id3 > id2);
    }

    @AfterAll
    private void destroyRedis() {
        System.out.println("--- destroyRedis ---");

        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
