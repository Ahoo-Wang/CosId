package me.ahoo.cosid.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentChainId;
import org.openjdk.jmh.annotations.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisIdSegmentDistributorBenchmark {

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;

    private DefaultSegmentId segmentIdBaseline;
    private DefaultSegmentId segmentId;
    private DefaultSegmentId segmentId100;
    private DefaultSegmentId segmentId1000;

    private SegmentChainId segmentChainIdBaseline;
    private SegmentChainId segmentChainId;
    private SegmentChainId segmentChainId100;
    private SegmentChainId segmentChainId1000;

    @Setup
    public void setup() {
        System.out.println("\n ----- RedisIdSegmentDistributorBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();

        segmentIdBaseline = new DefaultSegmentId(new IdSegmentDistributor.JdkIdSegmentDistributor());

        RedisIdSegmentDistributor redisMaxIdDistributor = new RedisIdSegmentDistributor("bh_seg", "1", 0, 1, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        segmentId = new DefaultSegmentId(redisMaxIdDistributor);
        RedisIdSegmentDistributor redisMaxIdDistributor100 = new RedisIdSegmentDistributor("bh_seg", "100", 0, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        segmentId100 = new DefaultSegmentId(redisMaxIdDistributor100);
        RedisIdSegmentDistributor redisMaxIdDistributor1000 = new RedisIdSegmentDistributor("bh_seg", "1000", 0, 1000, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        segmentId1000 = new DefaultSegmentId(redisMaxIdDistributor1000);

        segmentChainIdBaseline = new SegmentChainId(new IdSegmentDistributor.JdkIdSegmentDistributor());
        RedisIdSegmentDistributor redisMaxIdDistributorClain = new RedisIdSegmentDistributor("bh_clain", "1", 0, 1, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        segmentChainId = new SegmentChainId(redisMaxIdDistributorClain);
        RedisIdSegmentDistributor redisMaxIdDistributorClain100 = new RedisIdSegmentDistributor("bh_clain", "100", 0, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        segmentChainId100 = new SegmentChainId(redisMaxIdDistributorClain100);
        RedisIdSegmentDistributor redisMaxIdDistributorClain1000 = new RedisIdSegmentDistributor("bh_clain", "1000", 0, 1000, RedisIdSegmentDistributor.DEFAULT_TIMEOUT, redisClient.connect().async());
        segmentChainId1000 = new SegmentChainId(redisMaxIdDistributorClain1000);

    }

    @Benchmark
    @Threads(1)
    public long segmentId_baseline() {
        return segmentIdBaseline.generate();
    }

    @Benchmark
    @Threads(28)
    public long segmentId_step_1() {
        return segmentId.generate();
    }

    @Threads(1)
    @Benchmark
    public long segmentId_step_100() {
        return segmentId100.generate();
    }

    @Threads(1)
    @Benchmark
    public long segmentId_step_1000() {
        return segmentId1000.generate();
    }


    @Benchmark
    @Threads(1)
    public long segmentChainId_baseline() {
        return segmentChainIdBaseline.generate();
    }

    @Benchmark
    @Threads(28)
    public long segmentChainId_step_1() {
        return segmentChainId.generate();
    }

    @Threads(1)
    @Benchmark
    public long segmentChainId_step_100() {
        return segmentChainId100.generate();
    }

    @Threads(1)
    @Benchmark
    public long segmentChainId_step_1000() {
        return segmentChainId1000.generate();
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
