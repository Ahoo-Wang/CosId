package me.ahoo.cosid.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.CosId;

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosky.core.redis.RedisScripts;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static me.ahoo.cosid.redis.RedisMachineIdDistributor.hashTag;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisIdSegmentDistributor implements IdSegmentDistributor {

    public static final String REDIS_ID_GENERATE = "redis_id_generate.lua";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(1);
    public static final int DEFAULT_OFFSET = 0;

    private final String namespace;
    private final String name;
    /**
     * hash tag : namespace.name
     * cosid:{namespace.name}:adder
     */
    private final String adderKey;
    private final int offset;
    private final int step;
    private final Duration timeout;
    private final RedisClusterAsyncCommands<String, String> redisCommands;

    public RedisIdSegmentDistributor(String namespace,
                                     String name,
                                     RedisClusterAsyncCommands<String, String> redisCommands) {
        this(namespace, name, DEFAULT_OFFSET, DEFAULT_STEP, DEFAULT_TIMEOUT, redisCommands);
    }

    public RedisIdSegmentDistributor(String namespace,
                                     String name,
                                     int offset,
                                     int step,
                                     Duration timeout,
                                     RedisClusterAsyncCommands<String, String> redisCommands) {
        this.step = step;
        this.namespace = namespace;
        this.name = name;
        this.offset = offset;
        this.timeout = timeout;
        this.redisCommands = redisCommands;
        this.adderKey = CosId.COSID + ":" + hashTag(namespace + "." + name) + ".adder";
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public int getOffset() {
        return offset;
    }

    @Override
    public int getStep() {
        return step;
    }

    @SneakyThrows
    @Override
    public long nextMaxId(int step) {
        long maxId = fetchMaxIdAsync(step).get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        if (log.isDebugEnabled()) {
            log.debug("nextMaxId - step:[{}] - maxId:[{}].", step, maxId);
        }
        return maxId;
    }

    private CompletableFuture<Long> fetchMaxIdAsync(int step) {
        return RedisScripts.doEnsureScript(REDIS_ID_GENERATE, redisCommands,
                (scriptSha) -> {
                    String[] keys = {adderKey};
                    String[] values = {String.valueOf(offset), String.valueOf(step)};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.INTEGER, keys, values);
                }
        );
    }

}
