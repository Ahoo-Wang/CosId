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

package me.ahoo.cosid.spring.autoconfigure;

import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorInfo;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.runtime.CosIdRuntime;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * The machine slot's health (07 §4.2), replacing the 4.x {@code MachineIdHealthIndicator}: {@code DOWN} when the lease
 * was lost and not acquired again, or when a time-based generator's slot binding is no longer valid; otherwise
 * {@code UP}. Details: the slot, owner, fence, expiry and window ceiling with the milliseconds left, and the renewals
 * failed in a row with the last error. Without a time-based generator there is no slot, and it is {@code UP}.
 */
public class CosIdHealthIndicator implements HealthIndicator, GeneratorListener {
    private final ObjectProvider<CosIdRuntime> runtime;
    private final LongSupplier clock;
    private final AtomicInteger renewFailures = new AtomicInteger();
    private volatile Throwable lastRenewError;
    private volatile boolean lost;

    public CosIdHealthIndicator(ObjectProvider<CosIdRuntime> runtime) {
        this(runtime, System::currentTimeMillis);
    }

    CosIdHealthIndicator(ObjectProvider<CosIdRuntime> runtime, LongSupplier clock) {
        this.runtime = runtime;
        this.clock = clock;
    }

    @Override
    public Health health() {
        CosIdRuntime current = runtime.getIfAvailable();
        if (current == null) {
            return Health.unknown().build();
        }
        Map<String, Object> details = new LinkedHashMap<>();
        boolean down = lost;
        Optional<SlotLease> lease = current.slot();
        long now = clock.getAsLong();
        lease.ifPresent(held -> {
            details.put("slot", held.slot());
            details.put("owner", held.owner().key());
            details.put("fence", held.fence());
            details.put("expiresAt", held.expiresAt());
            details.put("expiresInMillis", held.expiresAt() - now);
            details.put("ceiling", held.ceiling());
            details.put("windowRemainingMillis", held.ceiling() - now);
        });
        Map<String, Object> bindings = new LinkedHashMap<>();
        for (Map.Entry<String, GeneratorInfo> entry : current.describe().entrySet()) {
            Object valid = entry.getValue().details().get("slotValid");
            if (valid != null) {
                bindings.put(entry.getKey(), valid);
                down |= Boolean.FALSE.equals(valid);
                details.putIfAbsent("slot", entry.getValue().details().get("slot"));
            }
        }
        if (details.isEmpty()) {
            details.put("slot", "none");
        }
        if (!bindings.isEmpty()) {
            details.put("slotValid", bindings);
        }
        details.put("lost", lost);
        details.put("renewFailures", renewFailures.get());
        Throwable error = lastRenewError;
        if (error != null) {
            details.put("lastRenewError", error.getClass().getName() + ": " + error.getMessage());
        }
        return (down ? Health.down() : Health.up()).withDetails(details).build();
    }

    @Override
    public void onSlotAcquired(SlotLease lease, String reason) {
        lost = false;
        renewFailures.set(0);
        lastRenewError = null;
    }

    @Override
    public void onSlotRenewed(SlotLease lease) {
        renewFailures.set(0);
        lastRenewError = null;
    }

    @Override
    public void onSlotRenewFailed(SlotLease lease, Throwable error) {
        renewFailures.incrementAndGet();
        lastRenewError = error;
    }

    @Override
    public void onSlotLost(SlotLease lease) {
        lost = true;
    }
}
