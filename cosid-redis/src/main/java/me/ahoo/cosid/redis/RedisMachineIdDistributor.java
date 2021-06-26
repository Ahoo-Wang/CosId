package me.ahoo.cosid.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.*;
import me.ahoo.cosky.core.redis.RedisScripts;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer.getBackwardsTimeStamp;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisMachineIdDistributor extends AbstractMachineIdDistributor {
    public static final String MACHINE_ID_DISTRIBUTE = "machine_id_distribute.lua";
    public static final String MACHINE_ID_REVERT = "machine_id_revert.lua";
    public static final String MACHINE_ID_REVERT_STABLE = "machine_id_revert_stable.lua";

    public static final int TIMEOUT = 5;
    private final RedisClusterAsyncCommands<String, String> redisCommands;

    public RedisMachineIdDistributor(RedisClusterAsyncCommands<String, String> redisCommands,
                                        LocalMachineState localMachineState,
                                        ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(localMachineState, clockBackwardsSynchronizer);
        this.redisCommands = redisCommands;
    }


    @SneakyThrows
    @Override
    protected MachineState distribute0(String namespace, int machineBit, InstanceId instanceId) {
        return distributeAsync0(namespace, machineBit, instanceId).get(TIMEOUT, TimeUnit.SECONDS);
    }

    protected CompletableFuture<MachineState> distributeAsync0(String namespace, int machineBit, InstanceId instanceId) {
        if (log.isInfoEnabled()) {
            log.info("distributeAsync0 - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", instanceId, machineBit, namespace);
        }
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

            long lastStamp = NOT_FOUND_LAST_STAMP;
            if (state.size() == 2) {
                lastStamp = state.get(1);
            }

            return MachineState.of(realMachineId, lastStamp);
        });
    }

    @SneakyThrows
    @Override
    protected void revert0(String namespace, InstanceId instanceId, MachineState machineState) {
        revertAsync0(namespace, instanceId, machineState).get(TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * when {@link InstanceId#isStable()} is true,do not revert machineId
     *
     * @param namespace
     * @param instanceId
     * @param machineState
     * @return
     */

    protected CompletableFuture<Void> revertAsync0(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("revertAsync - instanceId:[{}] @ namespace:[{}].", instanceId, namespace);
        }

        if (instanceId.isStable()) {
            return revertScriptAsync(MACHINE_ID_REVERT_STABLE, namespace, instanceId, machineState);
        }

        return revertScriptAsync(MACHINE_ID_REVERT, namespace, instanceId, machineState);
    }

    private CompletableFuture<Void> revertScriptAsync(String scriptName, String namespace, InstanceId instanceId, MachineState machineState) {
        return RedisScripts.doEnsureScript(scriptName, redisCommands,
                (scriptSha) -> {
                    long lastStamp = machineState.getLastTimeStamp();
                    if (getBackwardsTimeStamp(lastStamp) < 0) {
                        lastStamp = System.currentTimeMillis();
                    }
                    String[] keys = {namespace};
                    String[] values = {instanceId.getInstanceId(), String.valueOf(lastStamp)};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.INTEGER, keys, values);
                }
        );
    }

}
