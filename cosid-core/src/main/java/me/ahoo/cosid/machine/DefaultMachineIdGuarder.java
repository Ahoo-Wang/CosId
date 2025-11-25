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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
    private final ConcurrentHashMap<NamespacedInstanceId, GuardianState> registeredInstanceIds;
    private final MachineIdDistributor machineIdDistributor;
    private final ScheduledExecutorService executorService;
    private final Duration initialDelay;
    private final Duration delay;
    private final Duration safeGuardDuration;
    private volatile ScheduledFuture<?> scheduledFuture;
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
        this.registeredInstanceIds = new ConcurrentHashMap<>();
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
        return new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setDaemon(true).setNameFormat("MachineIdGuarder").build());
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation returns an immutable copy of the current guardian status map,
     * showing the status of all registered instances.
     */
    @Override
    public Map<NamespacedInstanceId, GuardianState> getGuardianStates() {
        return ImmutableMap.copyOf(registeredInstanceIds);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation adds the instance to an internal set for periodic guarding.
     * If the instance is already registered, it will not be added again.
     */
    @Override
    public void register(String namespace, InstanceId instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(namespace, instanceId);
        boolean absent = registeredInstanceIds.put(namespacedInstanceId, GuardianState.INITIAL) == null;
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
        registeredInstanceIds.remove(new NamespacedInstanceId(namespace, instanceId));
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
            log.debug("Start registered Instances:[{}].", registeredInstanceIds.size());
        }
        if (running.compareAndSet(false, true)) {
            scheduledFuture = executorService.scheduleWithFixedDelay(this::safeGuard, initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
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
            log.debug("Safe guard registered Instances:[{}].", registeredInstanceIds.size());
        }
        for (NamespacedInstanceId registeredInstance : registeredInstanceIds.keySet()) {
            long guardAt = System.currentTimeMillis();
            try {
                machineIdDistributor.guard(registeredInstance.getNamespace(), registeredInstance.getInstanceId(), safeGuardDuration);
                registeredInstanceIds.put(registeredInstance, GuardianState.success(guardAt));
            } catch (Throwable throwable) {
                registeredInstanceIds.put(registeredInstance, GuardianState.failed(guardAt, throwable));
                if (log.isErrorEnabled()) {
                    log.error("Guard Failed:[{}]!", throwable.getMessage(), throwable);
                }
            }
        }
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
            log.debug("Stop registered Instances:[{}].", registeredInstanceIds.size());
        }
        if (running.compareAndSet(true, false)) {
            scheduledFuture.cancel(true);
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
