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

package me.ahoo.cosid.test.machine.lease;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Compatibility kit for the upgrade from 3.4: 3.4 instances (the 3.4 machine id distributor of a backend, writing the
 * 3.4 format) and 4.0 instances (a {@code LeaseStoreMachineIdDistributor} on the backend's {@code MachineLeaseStore})
 * sharing one namespace never hold the same machine id, and each takes over the other's expired and released machine
 * ids.
 */
public abstract class MixedLeaseFormatSpec {
    private static final int MACHINE_BIT = 2;
    private static final int SLOTS = 6;

    /**
     * A new 3.4 distributor, with its own local state storage.
     */
    protected abstract MachineIdDistributor v34();

    /**
     * A new 4.0 distributor on the lease store, with its own local state storage.
     */
    protected abstract MachineIdDistributor v40();

    /**
     * Do what a 3.4 instance whose host clock is behind does when it takes a released machine id: give it to
     * {@code instanceId} and write back the very same last timestamp, in the 3.4 format.
     */
    protected abstract void takeReleasedAs34WithTheSameTimestamp(String namespace, int machineId, String instanceId);

    private static String newNamespace(String prefix) {
        return prefix + "_" + MockIdGenerator.INSTANCE.generateAsString();
    }

    @Test
    public void randomDistributeAndRevertNeverShareMachineIds() {
        String namespace = newNamespace("mixedRandom");
        MachineIdDistributor[] distributors = new MachineIdDistributor[SLOTS];
        InstanceId[] instances = new InstanceId[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            distributors[slot] = slot % 2 == 0 ? v34() : v40();
            instances[slot] = InstanceId.of("instance-" + slot, slot % 3 == 0);
        }
        // Machine ids of live instances, and of stable instances that shut down (they keep theirs).
        Map<Integer, Integer> held = new HashMap<>();
        Set<Integer> live = new HashSet<>();
        SplittableRandom random = new SplittableRandom(42);
        for (int step = 0; step < 300; step++) {
            int slot = random.nextInt(SLOTS);
            if (live.contains(slot)) {
                if (random.nextBoolean()) {
                    distributors[slot].guard(namespace, instances[slot], MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
                } else {
                    distributors[slot].revert(namespace, instances[slot]);
                    live.remove(slot);
                    if (!instances[slot].isStable()) {
                        held.remove(slot);
                    }
                }
            } else {
                try {
                    MachineState state = distributors[slot].distribute(namespace, MACHINE_BIT, instances[slot],
                        MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
                    Integer kept = held.put(slot, state.getMachineId());
                    if (kept != null) {
                        assertThat("a stable instance gets its machine id back", state.getMachineId(), equalTo(kept));
                    }
                    live.add(slot);
                } catch (MachineIdOverflowException overflow) {
                    assertThat("an overflow only when every machine id is held", new HashSet<>(held.values()).size(), equalTo(4));
                }
            }
            Set<Integer> machineIds = new HashSet<>(held.values());
            assertThat("step " + step + ": " + held, machineIds.size(), equalTo(held.size()));
        }
    }

    @Test
    public void eachFormatTakesOverTheOthersExpiredLease() throws InterruptedException {
        String namespace = newNamespace("mixedExpired");
        Duration safeGuard = Duration.ofMillis(200);
        MachineIdDistributor newDistributor = v40();
        InstanceId newOwner = InstanceId.of("new-owner", false);
        blockOneOfTwoMachineIds(namespace);
        MachineState newState = newDistributor.distribute(namespace, 1, newOwner, safeGuard);

        Thread.sleep(500);
        MachineIdDistributor oldDistributor = v34();
        MachineState oldState = oldDistributor.distribute(namespace, 1, InstanceId.of("old-taker", false), safeGuard);
        assertThat(oldState.getMachineId(), equalTo(newState.getMachineId()));
        assertThrows(MachineIdLostException.class, () -> newDistributor.guard(namespace, newOwner, safeGuard));

        Thread.sleep(500);
        MachineState taken = v40().distribute(namespace, 1, InstanceId.of("new-taker", false), safeGuard);
        assertThat(taken.getMachineId(), equalTo(oldState.getMachineId()));
        assertThat("the 4.0 owner starts after the 3.4 owner's last timestamp", taken.getLastTimeStamp(),
            greaterThanOrEqualTo(oldState.getLastTimeStamp()));
    }

    /**
     * A stable instance holds one of the two machine ids of {@code machineBit = 1}, so the instances of a test compete
     * for the other one.
     */
    private void blockOneOfTwoMachineIds(String namespace) {
        v40().distribute(namespace, 1, InstanceId.of("blocker", true), MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
    }

    @Test
    public void eachFormatTakesTheOthersReleasedMachineId() {
        String namespace = newNamespace("mixedReleased");
        Duration safeGuard = MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION;
        MachineIdDistributor newDistributor = v40();
        InstanceId newOwner = InstanceId.of("new-owner", false);
        blockOneOfTwoMachineIds(namespace);
        MachineState newState = newDistributor.distribute(namespace, 1, newOwner, safeGuard);
        newDistributor.revert(namespace, newOwner);

        MachineIdDistributor oldDistributor = v34();
        InstanceId oldOwner = InstanceId.of("old-owner", false);
        MachineState oldState = oldDistributor.distribute(namespace, 1, oldOwner, safeGuard);
        assertThat(oldState.getMachineId(), equalTo(newState.getMachineId()));
        oldDistributor.revert(namespace, oldOwner);

        MachineState again = v40().distribute(namespace, 1, InstanceId.of("new-again", false), safeGuard);
        assertThat(again.getMachineId(), equalTo(oldState.getMachineId()));
        assertThat(again.getLastTimeStamp(), greaterThanOrEqualTo(oldState.getLastTimeStamp()));
    }

    /**
     * A 3.4 instance with a host clock behind takes a machine id that 4.0 released and writes back the very same
     * timestamp: 4.0 must not take it from that live 3.4 owner, even though the 4.0 owner's last renewal is older than
     * the safe guard.
     */
    @Test
    public void releasedThenTakenBy34WithTheSameTimestampIsNotTakenAgain() throws InterruptedException {
        String namespace = newNamespace("mixedSameTimestamp");
        Duration safeGuard = Duration.ofMillis(300);
        blockOneOfTwoMachineIds(namespace);
        MachineIdDistributor newDistributor = v40();
        InstanceId newOwner = InstanceId.of("new-owner", false);
        int machineId = newDistributor.distribute(namespace, 1, newOwner, safeGuard).getMachineId();
        Thread.sleep(600);
        newDistributor.revert(namespace, newOwner);
        
        takeReleasedAs34WithTheSameTimestamp(namespace, machineId, "lagging-34");
        
        assertThrows(MachineIdOverflowException.class,
            () -> v40().distribute(namespace, 1, InstanceId.of("new-claimer", false), safeGuard));
    }
}
