package me.ahoo.cosid.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosky.core.redis.RedisScripts;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static me.ahoo.cosid.redis.RedisMachineIdDistributor.hashTag;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisIdGenerator implements IdGenerator {
    public static final String REDIS_ID_GENERATE = "redis_id_generate.lua";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(1);
    public static final int DEFAULT_OFFSET = 0;
    public static final int DEFAULT_STEP = 100;

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

    private long maxId = -1;
    private long sequence = 0;

    public RedisIdGenerator(String namespace,
                            String name,
                            RedisClusterAsyncCommands<String, String> redisCommands) {
        this(namespace, name, DEFAULT_OFFSET, DEFAULT_STEP, DEFAULT_TIMEOUT, redisCommands);
    }

    public RedisIdGenerator(String namespace,
                            String name,
                            int offset,
                            int step,
                            Duration timeout,
                            RedisClusterAsyncCommands<String, String> redisCommands) {
        this.namespace = namespace;
        this.name = name;
        this.offset = offset;
        this.step = step;
        this.timeout = timeout;
        this.redisCommands = redisCommands;
        this.adderKey = CosId.COSID + ":" + hashTag(namespace + "." + name) + ".adder";
    }

    public String getNamespace() {
        return namespace;
    }

    public long getMaxId() {
        return maxId;
    }

    public String getName() {
        return name;
    }

    public int getOffset() {
        return offset;
    }

    public int getStep() {
        return step;
    }

    @SneakyThrows
    @Override
    public long generate() {
        if (step == 1) {
            return fetchId();
        }

        synchronized (this) {
            while (true) {
                if (sequence < maxId) {
                    return ++sequence;
                }
                fetchIdAndReset();
            }
        }
    }

    @SneakyThrows
    private void fetchIdAndReset() {
        final long preSequence = sequence;
        maxId = fetchId();
        sequence = maxId - step;
        if (log.isInfoEnabled()) {
            log.info("fetchIdAndResetMaxId - namespace:[{}] - name:[{}] - maxId:[{}] - sequence:[{}->{}] - step:[{}].", namespace, name, maxId, preSequence, sequence, step);
        }
    }

    @SneakyThrows
    private long fetchId() {
        return fetchIdAsync().get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    private CompletableFuture<Long> fetchIdAsync() {
        return RedisScripts.doEnsureScript(REDIS_ID_GENERATE, redisCommands,
                (scriptSha) -> {
                    String[] keys = {adderKey};
                    String[] values = {String.valueOf(offset), String.valueOf(step)};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.INTEGER, keys, values);
                }
        );
    }

}
