package me.ahoo.cosid.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.SegmentId;
import org.openjdk.jmh.annotations.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisIdSegmentDistributorBenchmark {

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private SegmentId segmentId;
    private SegmentId segmentId100;
    private SegmentId segmentId1000;
//    private JdkId jdkId = new JdkId();

    @Setup
    public void setup() {
        System.out.println("\n ----- RedisIdSegmentDistributorBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        RedisIdSegmentDistributor redisMaxIdDistributor = new RedisIdSegmentDistributor("bh", "1", 0, 1, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        segmentId = new DefaultSegmentId(redisMaxIdDistributor);
        RedisIdSegmentDistributor redisMaxIdDistributor100 = new RedisIdSegmentDistributor("bh", "100", 0, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        segmentId100 = new DefaultSegmentId(redisMaxIdDistributor100);
        RedisIdSegmentDistributor redisMaxIdDistributor1000 = new RedisIdSegmentDistributor("bh", "1000", 0, 1000, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        segmentId1000 = new DefaultSegmentId(redisMaxIdDistributor1000);
    }

    @Benchmark
    @Threads(28)
    public long step_1() {
        return segmentId.generate();
    }

    @Threads(1)
    @Benchmark
    public long step_100() {
        return segmentId100.generate();
    }

    @Threads(1)
    @Benchmark
    public long step_1000() {
        return segmentId1000.generate();
    }

    @TearDown
    public void tearDown() {
        System.out.println("\n ----- RedisIdSegmentDistributorBenchmark tearDown ----- \n");
        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
