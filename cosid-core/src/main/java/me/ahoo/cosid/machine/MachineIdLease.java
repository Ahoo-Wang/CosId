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
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * The local view of how long this instance may keep using its machine id.
 *
 * <p>Other instances may take a machine id over once its owner has not renewed it for {@code safeGuardDuration}.
 * The lease is therefore valid until the start of the last successful renewal plus {@code safeGuardDuration},
 * minus a margin for clock skew between hosts. Generators check it before every id, so an instance that cannot renew
 * its lease stops generating ids before another instance can reuse the machine id.
 *
 * <p>Thread-safe; {@link #isValid(long)} is a pair of volatile reads.
 */
public final class MachineIdLease {
    /**
     * Upper bound of the clock skew margin.
     */
    public static final Duration MAX_CLOCK_SKEW_MARGIN = Duration.ofSeconds(10);
    /**
     * A lease that never expires, used when machine ids are never recycled.
     */
    public static final MachineIdLease FOREVER = new MachineIdLease(null, null, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
    
    @Nullable
    private final String namespace;
    @Nullable
    private final InstanceId instanceId;
    private final long validityMillis;
    private volatile long validUntil;
    private volatile boolean lost;
    
    public MachineIdLease(@Nullable String namespace, @Nullable InstanceId instanceId, Duration safeGuardDuration) {
        Preconditions.checkNotNull(safeGuardDuration, "safeGuardDuration can not be null!");
        this.namespace = namespace;
        this.instanceId = instanceId;
        this.validityMillis = validityMillis(safeGuardDuration);
        this.validUntil = this.validityMillis == Long.MAX_VALUE ? Long.MAX_VALUE : Long.MIN_VALUE;
    }
    
    /**
     * How long a renewal keeps the lease valid: {@code safeGuardDuration} minus {@code min(10s, safeGuardDuration / 10)}.
     */
    static long validityMillis(Duration safeGuardDuration) {
        if (MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION.equals(safeGuardDuration)) {
            return Long.MAX_VALUE;
        }
        long safeGuardMillis = safeGuardDuration.toMillis();
        long margin = Math.min(MAX_CLOCK_SKEW_MARGIN.toMillis(), safeGuardMillis / 10);
        return safeGuardMillis - margin;
    }
    
    /**
     * Extend the lease after a successful distribute or guard.
     *
     * @param renewedAt the time, in milliseconds, at which the renewal request was started
     */
    public void renew(long renewedAt) {
        if (validityMillis == Long.MAX_VALUE) {
            return;
        }
        long next = saturatedAdd(renewedAt, validityMillis);
        synchronized (this) {
            if (next > validUntil) {
                validUntil = next;
            }
        }
    }
    
    /**
     * The remote store reported that the machine id now belongs to another instance. The lease never becomes valid again.
     */
    public void markLost() {
        lost = true;
    }
    
    public boolean isLost() {
        return lost;
    }
    
    public long getValidUntil() {
        return validUntil;
    }
    
    public boolean isValid(long now) {
        return !lost && now <= validUntil;
    }
    
    /**
     * Throw {@link MachineIdLostException} if the lease is lost or expired.
     */
    public void ensureValid() throws MachineIdLostException {
        if (validityMillis == Long.MAX_VALUE && !lost) {
            return;
        }
        if (!isValid(System.currentTimeMillis())) {
            throw new MachineIdLostException(String.valueOf(namespace), instanceId, null);
        }
    }
    
    private static long saturatedAdd(long left, long right) {
        long result = left + right;
        if (((left ^ result) & (right ^ result)) < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }
    
    @Override
    public String toString() {
        return "MachineIdLease{"
            + "namespace=" + namespace
            + ", instanceId=" + instanceId
            + ", validUntil=" + validUntil
            + ", lost=" + lost
            + '}';
    }
}
