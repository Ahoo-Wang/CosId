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

package me.ahoo.cosid.snowflake.machine;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default MachineId Guarder implementation.
 *
 * @author ahoo wang
 */
@Slf4j
public class DefaultMachineIdGuarder implements MachineIdGuarder {
    
    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofMinutes(1);
    public static final Duration DEFAULT_DELAY = Duration.ofMinutes(1);
    private final CopyOnWriteArraySet<NamespacedInstanceId> registeredInstances;
    private final MachineIdDistributor machineIdDistributor;
    private final ScheduledExecutorService executorService;
    private final Duration initialDelay;
    private final Duration delay;
    private volatile ScheduledFuture<?> scheduledFuture;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public DefaultMachineIdGuarder(MachineIdDistributor machineIdDistributor) {
        this(machineIdDistributor, executorService(), DEFAULT_INITIAL_DELAY, DEFAULT_DELAY);
    }
    
    public DefaultMachineIdGuarder(MachineIdDistributor machineIdDistributor, Duration initialDelay, Duration delay) {
        this(machineIdDistributor, executorService(), initialDelay, delay);
    }
    
    public DefaultMachineIdGuarder(MachineIdDistributor machineIdDistributor, ScheduledExecutorService executorService,
                                   Duration initialDelay, Duration delay) {
        this.registeredInstances = new CopyOnWriteArraySet<>();
        this.machineIdDistributor = machineIdDistributor;
        this.executorService = executorService;
        this.initialDelay = initialDelay;
        this.delay = delay;
    }
    
    private static ScheduledExecutorService executorService() {
        return new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setDaemon(true).setNameFormat("DefaultMachineIdGuarder-").build());
    }
    
    @Override
    public void register(String namespace, InstanceId instanceId) {
        registeredInstances.add(new NamespacedInstanceId(namespace, instanceId));
    }
    
    @Override
    public void unregister(String namespace, InstanceId instanceId) {
        registeredInstances.remove(new NamespacedInstanceId(namespace, instanceId));
    }
    
    @Override
    public void start() {
        if (log.isDebugEnabled()) {
            log.debug("start - registeredInstances:[{}].", registeredInstances.size());
        }
        if (running.compareAndSet(false, true)) {
            scheduledFuture = executorService.scheduleWithFixedDelay(this::safeGuard, initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
    
    private void safeGuard() {
        if (log.isDebugEnabled()) {
            log.debug("safeGuard - registeredInstances:[{}].", registeredInstances.size());
        }
        for (NamespacedInstanceId registeredInstance : registeredInstances) {
            try {
                machineIdDistributor.guard(registeredInstance.getNamespace(), registeredInstance.getInstanceId());
            } catch (Throwable throwable) {
                if (log.isErrorEnabled()) {
                    log.error("Guard Failed:[{}]!", throwable.getMessage(), throwable);
                }
            }
        }
    }
    
    @Override
    public void stop() {
        if (log.isDebugEnabled()) {
            log.debug("stop - registeredInstances:[{}].", registeredInstances.size());
        }
        if (running.compareAndSet(true, false)) {
            scheduledFuture.cancel(true);
        }
    }
    
    @Override
    public boolean isRunning() {
        return running.get();
    }
    
}
