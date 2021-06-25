package me.ahoo.cosid.snowflake.machine;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@Slf4j
public abstract class AbstractMachineIdDistributor implements MachineIdDistributor {
    public static final int NOT_FOUND_LAST_STAMP = -1;
    private final int DEFAULT_TIMEOUT = 1;
    private final Duration timeout = Duration.ofSeconds(DEFAULT_TIMEOUT);
    private final LocalMachineState localMachineState;

    protected AbstractMachineIdDistributor(LocalMachineState localMachineState) {
        this.localMachineState = localMachineState;
    }

    protected long getBackwardsStamp(long lastStamp) {
        return lastStamp - System.currentTimeMillis();
    }

    /**
     * fix {@link ClockBackwardsException}
     *
     * @param lastStamp
     */
    @SneakyThrows
    protected void waitUntilLastStamp(long lastStamp) {
        long backwardsStamp = getBackwardsStamp(lastStamp);
        if (backwardsStamp <= 0) {
            return;
        }
        if (log.isWarnEnabled()) {
            log.warn("waitUntilLastStamp - backwardsStamp:[{}] - lastStamp:[{}].", backwardsStamp, lastStamp);
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
            waitUntilLastStamp(localState.getLastStamp());
            return localState.getMachineId();
        }

        localState = distribute0(namespace, machineBit, instanceId);
        if (getBackwardsStamp(localState.getLastStamp()) > 0) {
            waitUntilLastStamp(localState.getLastStamp());
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
        if (getBackwardsStamp(lastLocalState.getLastStamp()) < 0) {
            localMachineState.set(namespace, lastLocalState.getMachineId(), instanceId);
        }
        revert0(namespace, instanceId, lastLocalState);
    }


    protected abstract void revert0(String namespace, InstanceId instanceId, MachineState machineState);

}
