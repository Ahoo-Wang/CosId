package me.ahoo.cosid.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosky.core.redis.RedisScripts;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisIdGenerator implements IdGenerator {
    public static final String REDIS_ID_GENERATE = "redis_id_generate.lua";
    public static final int TIMEOUT = 1;
    public static final int DEFAULT_START = 1;
    public static final int DEFAULT_STEP = 100;

    private final String namespace;
    private final String name;
    private final int step;
    private final RedisClusterAsyncCommands<String, String> redisCommands;

    private volatile long maxId = -1;
    private long sequence;

    public RedisIdGenerator(String namespace,
                            String name,
                            int step,
                            RedisClusterAsyncCommands<String, String> redisCommands) {
        this.namespace = namespace;
        this.name = name;
        this.step = step;
        this.redisCommands = redisCommands;
        this.sequence = this.fetchIdAndResetMaxId() - 1;
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
                fetchIdAndResetMaxId();
            }
        }
    }

    @SneakyThrows
    private long fetchIdAndResetMaxId() {
        final long lastFetchId = fetchId();
        maxId = lastFetchId + step;
        return lastFetchId;
    }

    @SneakyThrows
    private long fetchId() {
        return fetchIdAsync().get(TIMEOUT, TimeUnit.SECONDS);
    }

    private CompletableFuture<Long> fetchIdAsync() {
        if (log.isInfoEnabled()) {
            log.info("fetchIdAsync - current Max ID:[{}] - step:[{}].", maxId, step);
        }
        return RedisScripts.doEnsureScript(REDIS_ID_GENERATE, redisCommands,
                (scriptSha) -> {
                    String[] keys = {namespace, name};
                    String[] values = {String.valueOf(step)};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.INTEGER, keys, values);
                }
        );
    }
}
