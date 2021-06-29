package me.ahoo.cosid.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.openjdk.jmh.annotations.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisIdGeneratorBenchmark {

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private RedisIdGenerator redisIdGenerator;
    private RedisIdGenerator redisIdGenerator100;
    private RedisIdGenerator redisIdGenerator1000;
//    private JdkId jdkId = new JdkId();

    @Setup
    public void setup() {
        System.out.println("\n ----- RedisIdGeneratorBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        redisIdGenerator = new RedisIdGenerator("bh", "1", 0, 1, RedisIdGenerator.DEFAULT_TIMEOUT, redisClient.connect().async());
        redisIdGenerator100 = new RedisIdGenerator("bh", "100", 0, 100, RedisIdGenerator.DEFAULT_TIMEOUT, redisClient.connect().async());
        redisIdGenerator1000 = new RedisIdGenerator("bh", "1000", 0, 1000, RedisIdGenerator.DEFAULT_TIMEOUT, redisClient.connect().async());
    }

    @Benchmark
    @Threads(28)
    public long step_1() {
        return redisIdGenerator.generate();
    }

    @Threads(1)
    @Benchmark
    public long step_100() {
        return redisIdGenerator100.generate();
    }

    @Threads(1)
    @Benchmark
    public long step_1000() {
        return redisIdGenerator1000.generate();
    }

    @TearDown
    public void tearDown() {
        System.out.println("\n ----- RedisIdGeneratorBenchmark tearDown ----- \n");
        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
