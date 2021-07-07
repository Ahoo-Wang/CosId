package me.ahoo.cosid.redis;


import io.lettuce.core.RedisClient;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.SegmentId;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ahoo wang
 */
public class RedisIdFactory implements AutoCloseable {

    public static final RedisIdFactory INSTANCE = new RedisIdFactory();

    AtomicInteger counter = new AtomicInteger();
    RedisClient redisClient;

    public synchronized RedisIdSegmentDistributor createDistributor(int step) {
        if (redisClient == null) {
            redisClient = RedisClient.create("redis://localhost:6379");
        }
        String namespace = "rbh-" + counter.incrementAndGet();
        return new RedisIdSegmentDistributor(
                namespace,
                String.valueOf(step),
                0,
                step,
                RedisIdSegmentDistributor.DEFAULT_TIMEOUT,
                redisClient.connect().async());
    }


    public DefaultSegmentId createSegmentId(int step) {
        RedisIdSegmentDistributor distributor = createDistributor(step);
        return new DefaultSegmentId(distributor);
    }

    public SegmentChainId createSegmentChainId(int step) {
        RedisIdSegmentDistributor distributor = createDistributor(step);
        return new SegmentChainId(distributor);
    }

    @Override
    public void close() {
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
