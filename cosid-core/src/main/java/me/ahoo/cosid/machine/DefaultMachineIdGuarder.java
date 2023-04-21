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
    private final CopyOnWriteArraySet<NamespacedInstanceId> registeredInstanceIds;
    private final MachineIdDistributor machineIdDistributor;
    private final ScheduledExecutorService executorService;
    private final Duration initialDelay;
    private final Duration delay;
    private final Duration safeGuardDuration;
    private volatile ScheduledFuture<?> scheduledFuture;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public DefaultMachineIdGuarder(MachineIdDistributor machineIdDistributor, Duration safeGuardDuration) {
        this(machineIdDistributor, executorService(), DEFAULT_INITIAL_DELAY, DEFAULT_DELAY, safeGuardDuration);
    }
    
    public DefaultMachineIdGuarder(MachineIdDistributor machineIdDistributor, ScheduledExecutorService executorService,
                                   Duration initialDelay, Duration delay, Duration safeGuardDuration) {
        this.registeredInstanceIds = new CopyOnWriteArraySet<>();
        this.machineIdDistributor = machineIdDistributor;
        this.executorService = executorService;
        this.initialDelay = initialDelay;
        this.delay = delay;
        this.safeGuardDuration = safeGuardDuration;
    }
    
    public static ScheduledExecutorService executorService() {
        return new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setDaemon(true).setNameFormat("DefaultMachineIdGuarder-").build());
    }
    
    @Override
    public void register(String namespace, InstanceId instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(namespace, instanceId);
        boolean absent = registeredInstanceIds.add(namespacedInstanceId);
        if (log.isDebugEnabled()) {
            log.debug("Register Instance:[{}] - [{}].", namespacedInstanceId, absent);
        }
    }
    
    @Override
    public void unregister(String namespace, InstanceId instanceId) {
        registeredInstanceIds.remove(new NamespacedInstanceId(namespace, instanceId));
    }
    
    public CopyOnWriteArraySet<NamespacedInstanceId> getRegisteredInstanceIds() {
        return registeredInstanceIds;
    }
    
    @Override
    public void start() {
        if (log.isDebugEnabled()) {
            log.debug("Start registered Instances:[{}].", registeredInstanceIds.size());
        }
        if (running.compareAndSet(false, true)) {
            scheduledFuture = executorService.scheduleWithFixedDelay(this::safeGuard, initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
    
    private void safeGuard() {
        if (log.isDebugEnabled()) {
            log.debug("Safe guard registered Instances:[{}].", registeredInstanceIds.size());
        }
        for (NamespacedInstanceId registeredInstance : registeredInstanceIds) {
            try {
                machineIdDistributor.guard(registeredInstance.getNamespace(), registeredInstance.getInstanceId(), safeGuardDuration);
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
            log.debug("Stop registered Instances:[{}].", registeredInstanceIds.size());
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
