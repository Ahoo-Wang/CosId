package me.ahoo.cosid.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.InstanceId;
import me.ahoo.cosid.MachineIdDistributor;
import me.ahoo.cosid.MachineIdOverflowException;
import me.ahoo.cosid.snowflake.ClockBackwardsException;
import me.ahoo.cosky.core.redis.RedisScripts;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisMachineIdDistributor implements MachineIdDistributor {
    public static final String MACHINE_ID_DISTRIBUTE = "machine_id_distribute.lua";
    public static final String MACHINE_ID_REVERT = "machine_id_revert.lua";
    public static final int TIMEOUT = 5;
    private final RedisClusterAsyncCommands<String, String> redisCommands;

    public RedisMachineIdDistributor(RedisClusterAsyncCommands<String, String> redisCommands) {
        this.redisCommands = redisCommands;
    }

    @SneakyThrows
    @Override
    public int distribute(String namespace, int machineBit, InstanceId instanceId) {
        return distributeAsync(namespace, machineBit, instanceId).get(TIMEOUT, TimeUnit.SECONDS);
    }

    @Override
    public CompletableFuture<Integer> distributeAsync(String namespace, int machineBit, InstanceId instanceId) {
        return RedisScripts.doEnsureScript(MACHINE_ID_DISTRIBUTE, redisCommands,
                (scriptSha) -> {
                    String[] keys = {namespace};
                    String[] values = {instanceId.getInstanceId(), String.valueOf(maxMachineId(machineBit))};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.MULTI, keys, values);
                }
        ).thenApply(distribution -> {
            List<Long> state = (List<Long>) distribution;
            int realMachineId = state.get(0).intValue();
            if (realMachineId == -1) {
                throw new MachineIdOverflowException(totalMachineIds(machineBit), instanceId);
            }
            long lastStamp = 0;
            if (state.size() == 2) {
                lastStamp = state.get(1);
                waitUntilLastStamp(lastStamp);
            }
            if (log.isInfoEnabled()) {
                log.info("distributeAsync - instanceId:[{}] @ namespace:[{}] - machineId:[{}] - lastStamp:[{}].", instanceId, namespace, realMachineId, lastStamp);
            }
            return realMachineId;
        });
    }

    /**
     * fix {@link me.ahoo.cosid.snowflake.ClockBackwardsException}
     *
     * @param lastStamp
     */
    @SneakyThrows
    private void waitUntilLastStamp(long lastStamp) {
        long backwardsStamp = getBackwardsStamp(lastStamp);
        if (backwardsStamp <= 0) {
            return;
        }

        if (backwardsStamp <= 10) {
            while ((getBackwardsStamp(lastStamp)) <= 0) {
                /**
                 * Spin until it catches the clock back
                 */
            }
        }

        if (backwardsStamp > 2000) {
            throw new ClockBackwardsException(lastStamp, System.currentTimeMillis());
        }

        TimeUnit.MILLISECONDS.sleep(backwardsStamp);
    }

    private long getBackwardsStamp(long lastStamp) {
        return lastStamp - System.currentTimeMillis();
    }

    @SneakyThrows
    @Override
    public void revert(String namespace, InstanceId instanceId) {
        revertAsync(namespace, instanceId).get(TIMEOUT, TimeUnit.SECONDS);
    }

    @Override
    public CompletableFuture<Void> revertAsync(String namespace, InstanceId instanceId) {
        if (log.isInfoEnabled()) {
            log.info("revertAsync - instanceId:[{}] @ namespace:[{}].", instanceId, namespace);
        }
        return RedisScripts.doEnsureScript(MACHINE_ID_REVERT, redisCommands,
                (scriptSha) -> {
                    String[] keys = {namespace};
                    String[] values = {instanceId.getInstanceId(), String.valueOf(System.currentTimeMillis())};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.INTEGER, keys, values);
                }
        );
    }
}
