package me.ahoo.cosid.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosky.core.redis.RedisScripts;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisIdGenerator implements IdGenerator {
    public static final String REDIS_ID_GENERATE = "redis_id_generate.lua";
    public static final int TIMEOUT = 1;
    public static final int DEFAULT_STEP = 100;

    private final String namespace;
    private final String name;
    private final int step;
    private final RedisClusterAsyncCommands<String, String> redisCommands;

    private volatile long maxId;
    private final AtomicLong sequence;

    public RedisIdGenerator(String namespace,
                            String name,
                            int step,
                            RedisClusterAsyncCommands<String, String> redisCommands) {
        this.namespace = namespace;
        this.name = name;
        this.step = step;
        this.redisCommands = redisCommands;
        this.sequence = new AtomicLong(0L);
        this.fetchId0();
    }

    public String getNamespace() {
        return namespace;
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
            return fetchIdAsync().get(TIMEOUT, TimeUnit.SECONDS);
        }
        synchronized (this) {
            long nextId = sequence.incrementAndGet();
            if (nextId < maxId) {
                return nextId;
            }

            if (log.isInfoEnabled()) {
                log.info("initFetchId - maxId:[{}] - step:[{}].", maxId, step);
            }
            fetchId0();
            return sequence.incrementAndGet();
        }
    }


    @SneakyThrows
    private void fetchId0() {
        if (log.isInfoEnabled()) {
            log.info("fetchId0 - maxId:[{}] - step:[{}].", maxId, step);
        }
        long lastFetchId = fetchIdAsync().get(TIMEOUT, TimeUnit.SECONDS);
        maxId = lastFetchId + step;
        sequence.set(lastFetchId - 1);
    }

    private CompletableFuture<Long> fetchIdAsync() {
        return RedisScripts.doEnsureScript(REDIS_ID_GENERATE, redisCommands,
                (scriptSha) -> {
                    String[] keys = {namespace, name};
                    String[] values = {String.valueOf(step)};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.INTEGER, keys, values);
                }
        );
    }
}
