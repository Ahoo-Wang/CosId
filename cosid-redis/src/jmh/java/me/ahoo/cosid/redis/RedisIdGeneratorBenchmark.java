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
    private RedisIdGenerator redisIdGenerator10000;

    @Setup
    public void setup() {
        System.out.println("\n ----- RedisIdGeneratorBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        redisIdGenerator = new RedisIdGenerator("bh", "1", 1, redisClient.connect().async());
        redisIdGenerator100 = new RedisIdGenerator("bh", "100", 100, redisClient.connect().async());
        redisIdGenerator1000 = new RedisIdGenerator("bh", "1000",  1000, redisClient.connect().async());
        redisIdGenerator10000 = new RedisIdGenerator("bh", "10000",  10000, redisClient.connect().async());
    }

    @Benchmark
    @Threads(30)
    public long step_1() {
        return redisIdGenerator.generate();
    }

    @Threads(20)
    @Benchmark
    public long step_100() {
        return redisIdGenerator100.generate();
    }

    @Threads(15)
    @Benchmark
    public long step_1000() {
        return redisIdGenerator1000.generate();
    }

    @Threads(10)
    @Benchmark
    public long step_10000() {
        return redisIdGenerator10000.generate();
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
