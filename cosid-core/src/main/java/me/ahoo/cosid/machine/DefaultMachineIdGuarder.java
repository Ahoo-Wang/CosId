/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosid.machine;

import me.ahoo.cosid.internal.Checks;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default MachineId Guarder implementation.
 *
 * <p>This class provides a default implementation of the {@link MachineIdGuarder} interface.
 * It uses a scheduled executor to periodically guard machine IDs for registered instances,
 * ensuring they remain active and preventing conflicts in distributed systems.
 *
 * <p>The guarder maintains a set of registered instance IDs and periodically calls the
 * {@link MachineIdDistributor#guard(String, InstanceId, Duration)} method for each registered instance.
 * This helps in scenarios where machine IDs need to be kept alive or refreshed at regular intervals.
 *
 * <p>Example usage:
 * <pre>{@code
 * MachineIdDistributor distributor = new SomeMachineIdDistributor();
 * Duration safeGuardDuration = Duration.ofMinutes(5);
 * DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, safeGuardDuration);
 * guarder.register("myNamespace", new InstanceId("instance1"));
 * guarder.start();
 * // ... application runs
 * guarder.stop();
 * }</pre>
 *
 * @author ahoo wang
 */
@Slf4j
public class DefaultMachineIdGuarder implements MachineIdGuarder {

    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofMinutes(1);
    public static final Duration DEFAULT_DELAY = Duration.ofMinutes(1);
    /**
     * How often the local machine state of each registered instance is refreshed, see
     * {@link MachineIdDistributor#refreshLocalState(String, InstanceId)}.
     */
    public static final Duration DEFAULT_LOCAL_STATE_INTERVAL = Duration.ofSeconds(1);
    private final ConcurrentHashMap<NamespacedInstanceId, GuardianState> guardianStates;
    private final ConcurrentHashMap<NamespacedInstanceId, MachineIdLease> leases = new ConcurrentHashMap<>();
    private final MachineIdDistributor machineIdDistributor;
    private final ScheduledExecutorService executorService;
    private final Duration initialDelay;
    private final Duration delay;
    private final Duration safeGuardDuration;
    private final Duration localStateInterval;
    private volatile ScheduledFuture<?> scheduledFuture;
    /**
     * Separate from {@link #executorService}: a remote guard that hangs must not hold back the local refresh.
     */
    private volatile ScheduledExecutorService localStateExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Constructs a DefaultMachineIdGuarder with default scheduling parameters.
     *
     * <p>This constructor creates a guarder with a default scheduled executor service,
     * initial delay of 1 minute, and delay of 1 minute between guard operations.
     *
     * @param machineIdDistributor the distributor used to guard machine IDs
     * @param safeGuardDuration    the duration for which to guard each machine ID
     */
    public DefaultMachineIdGuarder(MachineIdDistributor machineIdDistributor, Duration safeGuardDuration) {
        this(machineIdDistributor, executorService(), DEFAULT_INITIAL_DELAY, DEFAULT_DELAY, safeGuardDuration);
    }

    /**
     * Constructs a DefaultMachineIdGuarder with custom scheduling parameters.
     *
     * <p>This constructor allows full customization of the guarder's behavior,
     * including the executor service and scheduling intervals.
     *
     * @param machineIdDistributor the distributor used to guard machine IDs
     * @param executorService      the scheduled executor service for periodic guarding
     * @param initialDelay         the initial delay before the first guard operation
     * @param delay                the delay between subsequent guard operations
     * @param safeGuardDuration    the duration for which to guard each machine ID
     */
    public DefaultMachineIdGuarder(MachineIdDistributor machineIdDistributor, ScheduledExecutorService executorService,
                                   Duration initialDelay, Duration delay, Duration safeGuardDuration) {
        this(machineIdDistributor, executorService, initialDelay, delay, safeGuardDuration, DEFAULT_LOCAL_STATE_INTERVAL);
    }

    /**
     * Constructs a DefaultMachineIdGuarder that also refreshes the local machine state every {@code localStateInterval}.
     *
     * <p>The remote guard runs every {@code delay}, but the local state is the clock-backwards watermark used after a crash:
     * refreshing it more often (a local write only) bounds how far the clock may go back across a restart without being
     * detected to about {@code localStateInterval}.
     *
     * @param localStateInterval how often the local machine state is refreshed, zero or negative disables it
     */
    public DefaultMachineIdGuarder(MachineIdDistributor machineIdDistributor, ScheduledExecutorService executorService,
                                   Duration initialDelay, Duration delay, Duration safeGuardDuration, Duration localStateInterval) {
        this.guardianStates = new ConcurrentHashMap<>();
        this.localStateInterval = localStateInterval;
        this.machineIdDistributor = machineIdDistributor;
        this.executorService = executorService;
        this.initialDelay = initialDelay;
        this.delay = delay;
        this.safeGuardDuration = safeGuardDuration;
    }

    /**
     * Creates a default scheduled executor service for the guarder.
     *
     * <p>This method returns a single-threaded scheduled executor service with a daemon thread
     * named "MachineIdGuarder". The executor is suitable for periodic guarding operations.
     *
     * @return a new ScheduledExecutorService configured for machine ID guarding
     */
    public static ScheduledExecutorService executorService() {
        return new ScheduledThreadPoolExecutor(1, Checks.daemonThreadFactory("MachineIdGuarder"));
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation returns an immutable copy of the current guardian status map,
     * showing the status of all registered instances.
     */
    @Override
    public Map<NamespacedInstanceId, GuardianState> getGuardianStates() {
        return Map.copyOf(guardianStates);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation adds the instance to an internal set for periodic guarding.
     * If the instance is already registered, it will not be added again.
     */
    @Override
    public void register(String namespace, InstanceId instanceId) {
        register(namespace, instanceId, System.currentTimeMillis());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The lease is renewed from {@code distributedAt}, not from now: the distribute call may have taken a while.
     */
    @Override
    public void register(String namespace, InstanceId instanceId, long distributedAt) {
        Checks.checkArgument(!Checks.isNullOrEmpty(namespace), "namespace can not be empty!");
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(namespace, instanceId);
        boolean absent = guardianStates.put(namespacedInstanceId, GuardianState.INITIAL) == null;
        leases.computeIfAbsent(namespacedInstanceId, key -> new MachineIdLease(namespace, instanceId, safeGuardDuration))
            .renew(distributedAt);
        if (log.isDebugEnabled()) {
            log.debug("Register Instance:[{}] - [{}].", namespacedInstanceId, absent);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation removes the instance from the set of registered instances,
     * preventing further periodic guarding for this instance.
     */
    @Override
    public void unregister(String namespace, InstanceId instanceId) {
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(namespace, instanceId);
        guardianStates.remove(namespacedInstanceId);
        leases.remove(namespacedInstanceId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation schedules periodic guarding of registered instances using the configured
     * executor service. The first guard operation occurs after the initial delay, and subsequent
     * operations occur at the specified interval.
     */
    @Override
    public void start() {
        if (log.isDebugEnabled()) {
            log.debug("Start registered Instances:[{}].", guardianStates.size());
        }
        if (running.compareAndSet(false, true)) {
            scheduledFuture = executorService.scheduleWithFixedDelay(this::safeGuard, initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
            if (localStateInterval != null && !localStateInterval.isZero() && !localStateInterval.isNegative()) {
                long intervalMillis = localStateInterval.toMillis();
                localStateExecutor = new ScheduledThreadPoolExecutor(1,
                    Checks.daemonThreadFactory("MachineIdGuarder-LocalState"));
                localStateExecutor.scheduleWithFixedDelay(this::refreshLocalStates, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Performs the periodic guarding operation for all registered instances.
     *
     * <p>This method iterates through all registered instance IDs and calls the machine ID distributor's
     * guard method for each one. Any exceptions during guarding are logged but do not stop the process
     * for other instances.
     */
    void safeGuard() {
        if (log.isDebugEnabled()) {
            log.debug("Safe guard registered Instances:[{}].", guardianStates.size());
        }
        for (NamespacedInstanceId registeredInstance : guardianStates.keySet()) {
            long guardAt = System.currentTimeMillis();
            try {
                machineIdDistributor.guard(registeredInstance.getNamespace(), registeredInstance.getInstanceId(), safeGuardDuration);
                guardianStates.put(registeredInstance, GuardianState.success(guardAt));
                renewLease(registeredInstance, guardAt);
            } catch (Throwable throwable) {
                if (throwable instanceof MachineIdLostException) {
                    MachineIdLease lease = leases.get(registeredInstance);
                    if (lease != null) {
                        lease.markLost();
                    }
                }
                guardianStates.put(registeredInstance, GuardianState.failed(guardAt, throwable));
                if (log.isErrorEnabled()) {
                    log.error("Guard Failed:[{}]!", throwable.getMessage(), throwable);
                }
            }
        }
    }

    /**
     * Refreshes the local machine state (the clock-backwards watermark) of every registered instance.
     */
    void refreshLocalStates() {
        for (NamespacedInstanceId registeredInstance : guardianStates.keySet()) {
            try {
                machineIdDistributor.refreshLocalState(registeredInstance.getNamespace(), registeredInstance.getInstanceId());
            } catch (Throwable throwable) {
                if (log.isWarnEnabled()) {
                    log.warn("Refresh local state of [{}] failed!", registeredInstance, throwable);
                }
            }
        }
    }

    private void renewLease(NamespacedInstanceId registeredInstance, long guardAt) {
        MachineIdLease lease = leases.get(registeredInstance);
        if (lease != null) {
            lease.renew(guardAt);
        }
    }

    @Override
    public MachineIdLease getLease(String namespace, InstanceId instanceId) {
        MachineIdLease lease = leases.get(new NamespacedInstanceId(namespace, instanceId));
        return lease == null ? MachineIdLease.FOREVER : lease;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation cancels the scheduled guarding task and marks the guarder as stopped.
     * The cancellation is forceful, interrupting any ongoing guard operation.
     */
    @Override
    public void stop() {
        if (log.isDebugEnabled()) {
            log.debug("Stop registered Instances:[{}].", guardianStates.size());
        }
        if (running.compareAndSet(true, false)) {
            scheduledFuture.cancel(true);
            if (localStateExecutor != null) {
                localStateExecutor.shutdown();
                localStateExecutor = null;
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation returns the current running state, which is atomically managed.
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

}
