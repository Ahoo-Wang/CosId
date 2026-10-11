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

package me.ahoo.cosid.test.machine.distributor;

import static me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec.TEST_HOST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.TestSpec;

import com.google.common.base.Stopwatch;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assumptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The machine id of a stable instance is never recycled by other instances, even after its owner has been offline for
 * longer than the safe guard duration, and the owner gets it back when it starts again.
 */
public class DistributeStableNotRecycled implements TestSpec {
    /**
     * Small on purpose: every distribute below must finish within the safe guard window.
     */
    private static final int MACHINE_BIT = 2;
    private final Supplier<MachineIdDistributor> implFactory;
    private final Duration safeGuardDuration;

    public DistributeStableNotRecycled(Supplier<MachineIdDistributor> implFactory, Duration safeGuardDuration) {
        this.implFactory = implFactory;
        this.safeGuardDuration = safeGuardDuration;
    }

    @SneakyThrows
    @Override
    public void verify() {
        MachineIdDistributor distributor = implFactory.get();
        String namespace = MockIdGenerator.usePrefix("DistributeStableNotRecycled").generateAsString();
        int totalMachineIds = MachineIdDistributor.totalMachineIds(MACHINE_BIT);

        InstanceId stableOwner = InstanceId.of(TEST_HOST, 0, true);
        assertThat(distributor.distribute(namespace, MACHINE_BIT, stableOwner, safeGuardDuration).getMachineId(), equalTo(0));
        for (int i = 1; i < totalMachineIds; i++) {
            InstanceId temporaryOwner = InstanceId.of(TEST_HOST, i, false);
            assertThat(distributor.distribute(namespace, MACHINE_BIT, temporaryOwner, safeGuardDuration).getMachineId(), equalTo(i));
        }

        /*
         * Nobody guards: every owner, the stable one included, is now past the safe guard window.
         */
        Thread.sleep(safeGuardDuration.plusMillis(300).toMillis());

        Stopwatch stopwatch = Stopwatch.createStarted();
        List<Integer> recycledMachineIds = new ArrayList<>();
        for (int i = 0; i < totalMachineIds - 1; i++) {
            InstanceId newcomer = InstanceId.of(TEST_HOST, totalMachineIds + i, false);
            recycledMachineIds.add(distributor.distribute(namespace, MACHINE_BIT, newcomer, safeGuardDuration).getMachineId());
        }
        MachineIdOverflowException overflowException = null;
        try {
            int machineId = distributor.distribute(namespace, MACHINE_BIT, InstanceId.of(TEST_HOST, 2 * totalMachineIds, false), safeGuardDuration)
                .getMachineId();
            recycledMachineIds.add(machineId);
        } catch (MachineIdOverflowException expected) {
            overflowException = expected;
        }
        if (stopwatch.elapsed().compareTo(safeGuardDuration) > 0) {
            // The newcomers are never guarded either, so they may legitimately recycle each other.
            Assumptions.abort("recycle phase exceeded safe guard window: " + stopwatch.elapsed() + " > " + safeGuardDuration);
        }

        Integer[] temporaryMachineIds = new Integer[totalMachineIds - 1];
        for (int i = 0; i < temporaryMachineIds.length; i++) {
            temporaryMachineIds[i] = i + 1;
        }
        assertThat("only the machine ids of temporary instances are recycled", recycledMachineIds, containsInAnyOrder(temporaryMachineIds));
        assertThat(overflowException, instanceOf(MachineIdOverflowException.class));

        /*
         * The stable owner keeps its machine id when it starts again.
         */
        MachineIdDistributor restartedDistributor = implFactory.get();
        assertThat(restartedDistributor.distribute(namespace, MACHINE_BIT, stableOwner, safeGuardDuration).getMachineId(), equalTo(0));
    }
}
