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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * The health of the machine slots (07 §4.2, api-freeze.md §2.4), replacing the 4.x {@code MachineIdHealthIndicator}:
 * {@code DOWN} when a started machine lost its lease and did not acquire it again, or when the slot binding of a
 * time-based generator on it is no longer valid; otherwise {@code UP}. The details are
 * {@code machines: {<name>: {namespace, slot, valid, owner, fence, expiresAt, expiresInMillis, ceiling,
 * windowRemainingMillis, lost, renewFailures, lastRenewError}}}, one entry per started machine ({@code default} for the
 * default one; the lease fields only for a leased one). Without a time-based generator no machine starts: the details
 * are empty and it is {@code UP}.
 */
public class CosIdHealthIndicator implements HealthIndicator, GeneratorListener {
    private final ObjectProvider<CosIdRuntime> runtime;
    private final LongSupplier clock;
    /** The renewal state of each machine, by the namespace of its slot, unique in a runtime. */
    private final Map<String, LeaseState> states = new ConcurrentHashMap<>();

    public CosIdHealthIndicator(ObjectProvider<CosIdRuntime> runtime) {
        this(runtime, System::currentTimeMillis);
    }

    CosIdHealthIndicator(ObjectProvider<CosIdRuntime> runtime, LongSupplier clock) {
        this.runtime = runtime;
        this.clock = clock;
    }

    /** What the listener learned about the lease of one namespace. */
    private static final class LeaseState {
        private volatile boolean lost;
        private final AtomicInteger renewFailures = new AtomicInteger();
        private volatile Throwable lastRenewError;
    }

    private LeaseState state(String namespace) {
        return states.computeIfAbsent(namespace, k -> new LeaseState());
    }

    @Override
    public Health health() {
        CosIdRuntime current = runtime.getIfAvailable();
        if (current == null) {
            return Health.unknown().build();
        }
        Map<String, Map<String, Object>> machines = new LinkedHashMap<>();
        boolean down = false;
        // The generators tell the namespace, slot and validity of each machine they issue on.
        for (GeneratorInfo info : current.describe().values()) {
            Object machine = info.details().get("machine");
            if (machine == null) {
                continue;
            }
            Map<String, Object> details = machines.computeIfAbsent(machine.toString(), k -> new LinkedHashMap<>());
            details.putIfAbsent("namespace", info.details().get("machineNamespace"));
            details.putIfAbsent("slot", info.details().get("slot"));
            boolean valid = !Boolean.FALSE.equals(info.details().get("slotValid"));
            details.merge("valid", valid, (a, b) -> (Boolean) a && (Boolean) b);
            down |= !valid;
        }
        long now = clock.getAsLong();
        for (Map.Entry<String, Map<String, Object>> entry : machines.entrySet()) {
            Map<String, Object> details = entry.getValue();
            Optional<SlotLease> lease = current.slot(entry.getKey());
            lease.ifPresent(held -> {
                details.put("slot", held.slot());
                details.put("owner", held.owner().key());
                details.put("fence", held.fence());
                details.put("expiresAt", held.expiresAt());
                details.put("expiresInMillis", held.expiresAt() - now);
                details.put("ceiling", held.ceiling());
                details.put("windowRemainingMillis", held.ceiling() - now);
            });
            LeaseState state = states.get(String.valueOf(details.get("namespace")));
            boolean lost = state != null && state.lost;
            details.put("lost", lost);
            details.put("renewFailures", state == null ? 0 : state.renewFailures.get());
            Throwable error = state == null ? null : state.lastRenewError;
            if (error != null) {
                details.put("lastRenewError", error.getClass().getName() + ": " + error.getMessage());
            }
            down |= lost;
        }
        Health.Builder health = down ? Health.down() : Health.up();
        if (!machines.isEmpty()) {
            health.withDetail("machines", machines);
        }
        return health.build();
    }

    @Override
    public void onSlotAcquired(SlotLease lease, String reason) {
        LeaseState state = state(lease.namespace());
        state.lost = false;
        state.renewFailures.set(0);
        state.lastRenewError = null;
    }

    @Override
    public void onSlotRenewed(SlotLease lease) {
        LeaseState state = state(lease.namespace());
        state.renewFailures.set(0);
        state.lastRenewError = null;
    }

    @Override
    public void onSlotRenewFailed(SlotLease lease, Throwable error) {
        LeaseState state = state(lease.namespace());
        state.renewFailures.incrementAndGet();
        state.lastRenewError = error;
    }

    @Override
    public void onSlotLost(SlotLease lease) {
        state(lease.namespace()).lost = true;
    }
}
