package me.ahoo.cosid.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosky.core.redis.RedisScripts;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static me.ahoo.cosid.redis.RedisMachineIdDistributor.wrapNamespace;

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

    private long maxId = -1;
    private long sequence = 0;

    public RedisIdGenerator(String namespace,
                            String name,
                            int step,
                            RedisClusterAsyncCommands<String, String> redisCommands) {
        this.namespace = namespace;
        this.name = name;
        this.step = step;
        this.redisCommands = redisCommands;
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
            log.info("fetchIdAndResetMaxId - namespace:[{}] - name:[{}] maxId:[{}] - sequence:[{}->{}] - step:[{}].", namespace, name, maxId, preSequence, sequence, step);
        }
    }

    @SneakyThrows
    private long fetchId() {
        return fetchIdAsync().get(TIMEOUT, TimeUnit.SECONDS);
    }

    private CompletableFuture<Long> fetchIdAsync() {
        return RedisScripts.doEnsureScript(REDIS_ID_GENERATE, redisCommands,
                (scriptSha) -> {
                    String[] keys = {wrapNamespace(namespace)};
                    String[] values = {name, String.valueOf(step)};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.INTEGER, keys, values);
                }
        );
    }
}
