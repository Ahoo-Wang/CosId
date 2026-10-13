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

import me.ahoo.cosid.machine.InstanceId;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Receives what happens inside CosId, for metrics and alerts: clock and sequence waits, segment fetches, the prefetch,
 * machine id leases and backend call timeouts.
 *
 * <p>Every method does nothing by default, so a listener implements only what it needs. Events fire when something
 * happens, never once per generated id. Register one with {@link CosIdEvents#register(CosIdEventListener)}.</p>
 *
 * <p><b>Callbacks must be fast and non-blocking.</b> They run on the thread that raised the event, and some run while
 * that thread holds the generator's lock, so a slow callback stalls every caller of the generator:</p>
 * <ul>
 *   <li>{@link #onSequenceExhaustedWait(Duration)} runs inside the synchronized {@code generate()} of Snowflake
 *   generators and {@code CosIdGenerator};</li>
 *   <li>{@link #onSegmentFetch(String, Duration, Throwable)} runs under the lock of {@code DefaultSegmentId} and of a
 *   segment chain's next segment;</li>
 *   <li>{@link #onClockBackwards(String, long, long)} and {@link #onClockSyncWait(String, Duration)} run on the
 *   generating thread.</li>
 * </ul>
 * <p>Record a counter or a timer, or hand the event to another thread; never call a store, the network or a generator
 * from a callback. A callback that throws is logged and skipped, it never fails the caller (a
 * {@link VirtualMachineError} is rethrown).</p>
 *
 * @author ahoo wang
 * @since 4.1.0
 */
public interface CosIdEventListener {
    
    /**
     * The listener that does nothing: one shared instance.
     */
    CosIdEventListener NONE = new CosIdEventListener() {
        @Override
        public String toString() {
            return "CosIdEventListener.NONE";
        }
    };
    
    /**
     * The clock moved back while a generator was running.
     *
     * @param generator        the generator's class name
     * @param lastTimestamp    the last timestamp it used
     * @param currentTimestamp the clock's time, earlier
     */
    default void onClockBackwards(String generator, long lastTimestamp, long currentTimestamp) {
    }
    
    /**
     * A clock-sync generator waited for the clock to catch up after it moved back.
     */
    default void onClockSyncWait(String generator, Duration waited) {
    }
    
    /**
     * A generator used up the sequence of a tick and waited for the next one.
     */
    default void onSequenceExhaustedWait(Duration waited) {
    }
    
    /**
     * A segment generator fetched from its store.
     *
     * @param namespacedName the generator's {@code namespace.name}
     * @param latency        how long the fetch took
     * @param failure        {@code null} when it succeeded
     */
    default void onSegmentFetch(String namespacedName, Duration latency, @Nullable Throwable failure) {
    }
    
    /**
     * A segment chain was starving and grew its prefetch distance.
     */
    default void onPrefetchExpansion(String namespacedName, int fromDistance, int toDistance) {
    }
    
    /**
     * A segment chain's background prefetch failed.
     */
    default void onPrefetchFailure(String namespacedName, Throwable failure) {
    }
    
    /**
     * An instance took a machine id lease.
     */
    default void onLeaseAcquired(String namespace, InstanceId instanceId, int machineId, LeaseClaim claim) {
    }
    
    /**
     * A machine id lease was renewed.
     *
     * @param clockSkewMillis how far this host's clock is ahead of the store's (negative: behind), 0 when unknown
     */
    default void onLeaseRenewed(String namespace, InstanceId instanceId, int machineId, long clockSkewMillis) {
    }
    
    /**
     * Renewing a machine id lease failed.
     */
    default void onLeaseRenewFailed(String namespace, InstanceId instanceId, Throwable failure) {
    }
    
    /**
     * A machine id lease was lost: another instance owns the machine id now, and its generators fail fast.
     */
    default void onLeaseLost(String namespace, InstanceId instanceId) {
    }
    
    /**
     * A backend call bounded by a timeout timed out.
     */
    default void onCallTimeout(String operation, Duration timeout) {
    }
    
    /**
     * A backend call bounded by a timeout was rejected: too many calls in flight.
     */
    default void onCallRejected(String operation) {
    }
}
