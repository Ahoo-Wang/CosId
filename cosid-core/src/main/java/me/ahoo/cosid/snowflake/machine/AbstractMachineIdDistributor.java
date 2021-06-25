package me.ahoo.cosid.snowflake.machine;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;

import java.time.Duration;

/**
 * @author ahoo wang
 */
@Slf4j
public abstract class AbstractMachineIdDistributor implements MachineIdDistributor {
    public static final int NOT_FOUND_LAST_STAMP = -1;
    private final int DEFAULT_TIMEOUT = 1;
    private final Duration timeout = Duration.ofSeconds(DEFAULT_TIMEOUT);
    private final LocalMachineState localMachineState;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;

    public AbstractMachineIdDistributor(LocalMachineState localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this.localMachineState = localMachineState;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
    }

    /**
     * 1. get from {@link me.ahoo.cosid.snowflake.machine.LocalMachineState}
     * 2. when not found: {@link #distribute0}
     * 3. set {@link me.ahoo.cosid.snowflake.machine.MachineState} to {@link me.ahoo.cosid.snowflake.machine.LocalMachineState}
     *
     * @param namespace
     * @param machineBit
     * @param instanceId
     * @return
     * @throws MachineIdOverflowException
     */
    @SneakyThrows
    @Override
    public int distribute(String namespace, int machineBit, InstanceId instanceId) throws MachineIdOverflowException {
        MachineState localState = localMachineState.get(namespace, instanceId);
        if (!MachineState.NOT_FOUND.equals(localState)) {
            clockBackwardsSynchronizer.syncUninterruptibly(localState.getLastStamp());
            return localState.getMachineId();
        }

        localState = distribute0(namespace, machineBit, instanceId);
        if (ClockBackwardsSynchronizer.getBackwardsStamp(localState.getLastStamp()) > 0) {
            clockBackwardsSynchronizer.syncUninterruptibly(localState.getLastStamp());
            localState = MachineState.of(localState.getMachineId(), System.currentTimeMillis());
        }

        localMachineState.set(namespace, localState.getMachineId(), instanceId);
        return localState.getMachineId();
    }

    protected abstract MachineState distribute0(String namespace, int machineBit, InstanceId instanceId);


    /**
     * 1. get from {@link me.ahoo.cosid.snowflake.machine.LocalMachineState}
     * 2. when not found: {@link #distribute0} , no need to revert
     * 3. revert
     *
     * @param namespace
     * @param instanceId
     * @throws MachineIdOverflowException
     */
    @SneakyThrows
    @Override
    public void revert(String namespace, InstanceId instanceId) throws MachineIdOverflowException {
        MachineState lastLocalState = localMachineState.get(namespace, instanceId);
        if (MachineState.NOT_FOUND.equals(lastLocalState)) {
            revert0(namespace, instanceId, lastLocalState);
            return;
        }
        if (ClockBackwardsSynchronizer.getBackwardsStamp(lastLocalState.getLastStamp()) < 0) {
            localMachineState.set(namespace, lastLocalState.getMachineId(), instanceId);
        }
        revert0(namespace, instanceId, lastLocalState);
    }


    protected abstract void revert0(String namespace, InstanceId instanceId, MachineState machineState);

}
