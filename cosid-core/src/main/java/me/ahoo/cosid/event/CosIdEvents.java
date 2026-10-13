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

package me.ahoo.cosid.event;

import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.machine.InstanceId;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The registered {@link CosIdEventListener}s. CosId's components publish to {@link #listener()}, which is
 * {@link CosIdEventListener#NONE} while nothing is registered.
 *
 * @author ahoo wang
 * @since 4.1.0
 */
@Slf4j
public final class CosIdEvents {
    private static final List<CosIdEventListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile CosIdEventListener current = CosIdEventListener.NONE;
    
    private CosIdEvents() {
    }
    
    /**
     * The listener to publish to: {@link CosIdEventListener#NONE}, the only registered one, or one that calls all of them
     * and keeps going when one throws.
     */
    public static CosIdEventListener listener() {
        return current;
    }
    
    /**
     * Register a listener.
     *
     * @return closes the registration
     */
    public static AutoCloseable register(CosIdEventListener listener) {
        Checks.checkNotNull(listener, "listener can not be null!");
        LISTENERS.add(listener);
        refresh();
        return () -> {
            LISTENERS.remove(listener);
            refresh();
        };
    }
    
    private static synchronized void refresh() {
        if (LISTENERS.isEmpty()) {
            current = CosIdEventListener.NONE;
        } else if (LISTENERS.size() == 1) {
            current = new Guarded(List.of(LISTENERS.get(0)));
        } else {
            current = new Guarded(List.copyOf(LISTENERS));
        }
    }
    
    /**
     * Calls every listener; one that throws (anything but a {@link VirtualMachineError}) is logged and skipped, never
     * fails the caller.
     */
    private static final class Guarded implements CosIdEventListener {
        private final List<CosIdEventListener> listeners;
        
        Guarded(List<CosIdEventListener> listeners) {
            this.listeners = listeners;
        }
        
        private void each(Consumer<CosIdEventListener> event) {
            for (CosIdEventListener listener : listeners) {
                try {
                    event.accept(listener);
                } catch (VirtualMachineError fatal) {
                    throw fatal;
                } catch (Throwable listenerFailure) {
                    // Also errors and sneaky-thrown checked exceptions: an event is raised in finally blocks and under
                    // locks, where anything escaping would replace the caller's own exception.
                    if (log.isWarnEnabled()) {
                        log.warn("CosIdEventListener [{}] failed.", listener, listenerFailure);
                    }
                }
            }
        }
        
        @Override
        public void onClockBackwards(String generator, long lastTimestamp, long currentTimestamp) {
            each(listener -> listener.onClockBackwards(generator, lastTimestamp, currentTimestamp));
        }
        
        @Override
        public void onClockSyncWait(String generator, Duration waited) {
            each(listener -> listener.onClockSyncWait(generator, waited));
        }
        
        @Override
        public void onSequenceExhaustedWait(Duration waited) {
            each(listener -> listener.onSequenceExhaustedWait(waited));
        }
        
        @Override
        public void onSegmentFetch(String namespacedName, Duration latency, @Nullable Throwable failure) {
            each(listener -> listener.onSegmentFetch(namespacedName, latency, failure));
        }
        
        @Override
        public void onPrefetchExpansion(String namespacedName, int fromDistance, int toDistance) {
            each(listener -> listener.onPrefetchExpansion(namespacedName, fromDistance, toDistance));
        }
        
        @Override
        public void onPrefetchFailure(String namespacedName, Throwable failure) {
            each(listener -> listener.onPrefetchFailure(namespacedName, failure));
        }
        
        @Override
        public void onLeaseAcquired(String namespace, InstanceId instanceId, int machineId, LeaseClaim claim) {
            each(listener -> listener.onLeaseAcquired(namespace, instanceId, machineId, claim));
        }
        
        @Override
        public void onLeaseRenewed(String namespace, InstanceId instanceId, int machineId, long clockSkewMillis) {
            each(listener -> listener.onLeaseRenewed(namespace, instanceId, machineId, clockSkewMillis));
        }
        
        @Override
        public void onLeaseRenewFailed(String namespace, InstanceId instanceId, Throwable failure) {
            each(listener -> listener.onLeaseRenewFailed(namespace, instanceId, failure));
        }
        
        @Override
        public void onLeaseLost(String namespace, InstanceId instanceId) {
            each(listener -> listener.onLeaseLost(namespace, instanceId));
        }
        
        @Override
        public void onCallTimeout(String operation, Duration timeout) {
            each(listener -> listener.onCallTimeout(operation, timeout));
        }
        
        @Override
        public void onCallRejected(String operation) {
            each(listener -> listener.onCallRejected(operation));
        }
    }
}
