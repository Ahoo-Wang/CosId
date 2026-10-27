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

package me.ahoo.cosid.benchmark;

import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.segment.IdSegmentDistributor;

import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What the 4.1.0 starter builds by default, shared by the scenarios (benchmark/scenarios.md).
 */
final class Baseline4x {
    static final int MACHINE_ID = 1;
    /** The lease every 4.x time-based generator checks: {@code Leased*} wraps them in the starter. */
    static final MachineIdLease LEASE = MachineIdLease.FOREVER;
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private Baseline4x() {
    }

    /**
     * An in-memory distributor with no simulated latency, like the 5.x {@code InMemoryStore}.
     */
    static final class MemoryDistributor implements IdSegmentDistributor {
        private final String name;
        private final long step;
        private final AtomicLong maxId = new AtomicLong();

        MemoryDistributor(String name, long step) {
            this.name = name;
            this.step = step;
        }

        @Override
        public String getNamespace() {
            return "bench";
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getStep() {
            return step;
        }

        @Override
        public long nextMaxId(long step) {
            return maxId.addAndGet(step);
        }
    }
}
