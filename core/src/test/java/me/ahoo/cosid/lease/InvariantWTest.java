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

package me.ahoo.cosid.lease;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.store.tck.InMemoryStore;
import me.ahoo.cosid.store.tck.ManualClock;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;

/**
 * Invariant W (05-machine-lease.md §3) end to end: keepers issue timestamps by the client rule ({@code floor < t <=
 * ceiling} of a valid binding), including a crashed keeper that keeps issuing on its stale binding and clients whose
 * clocks are off the store clock; no timestamp of a slot is ever issued under two holdings.
 */
class InvariantWTest {
    private static final String NS = "w";
    private static final Duration TTL = Duration.ofSeconds(3);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);

    private final ManualClock storeClock = new ManualClock(1_700_000_000_000L);
    private final InMemoryStore store = new InMemoryStore(storeClock);
    private final List<Process> processes = new ArrayList<>();
    /** slot → timestamp → the holding (process and generation) that issued it. */
    private final Map<Integer, Map<Long, String>> issued = new HashMap<>();

    /** A runtime: its keeper, its clock offset and whether it still renews. */
    private final class Process {
        final int id;
        final long offset;
        final ManualTicker ticker = new ManualTicker();
        final LeaseKeeper keeper;
        boolean renewing = true;
        boolean dead;
        long lastIssued = Long.MIN_VALUE;

        Process(int id, long offset) {
            this.id = id;
            this.offset = offset;
            this.keeper = new LeaseKeeper(store, NS, new Owner("host-" + id % 3, UUID.randomUUID()), 1, TTL, TOLERANCE, OptionalInt.empty(),
                Duration.ZERO, task -> { }, () -> storeClock.currentMillis() + offset, GeneratorListener.NOOP, ticker);
        }

        /** Issues one timestamp now if the client rule allows it. */
        void issue() {
            if (dead) {
                return;
            }
            SlotBinding binding = keeper.current();
            long t = storeClock.currentMillis() + offset;
            if (!binding.isValid() || t <= binding.floor() || t > binding.ceiling()) {
                return;
            }
            String holding = id + "#" + binding.generation();
            String previous = issued.computeIfAbsent(binding.slot(), k -> new HashMap<>()).putIfAbsent(t, holding);
            if (previous != null && !previous.equals(holding)) {
                fail("slot " + binding.slot() + " issued " + t + " under " + previous + " and " + holding);
            }
            lastIssued = Math.max(lastIssued, t);
        }
    }

    private void advance(long millis) {
        storeClock.advance(Duration.ofMillis(millis));
        for (Process process : processes) {
            process.ticker.advance(Duration.ofMillis(millis));
        }
    }

    private Process start(int id, long offset) {
        Process process = new Process(id, offset);
        process.keeper.start();
        processes.add(process);
        return process;
    }

    private void issueAll() {
        processes.forEach(Process::issue);
    }

    @Test
    void takeoverWithoutReleaseStartsAboveTheCrashedHoldersCeiling() {
        Process crashed = start(0, 0);
        SlotBinding stale = crashed.keeper.current();
        crashed.renewing = false;
        Process other = start(1, 0);
        // Keep slot 1 busy so the next runtime has to wait for slot 0.
        for (int i = 0; i < 40; i++) {
            advance(100);
            if (i % 10 == 0) {
                other.keeper.runDue();
            }
            issueAll();
        }
        assertThat("the crashed holder issued up to its ceiling", crashed.lastIssued, is(stale.ceiling()));
        Process next = start(2, 0);
        SlotBinding taken = next.keeper.current();
        assertThat(taken.slot(), is(stale.slot()));
        assertThat(taken.floor(), greaterThanOrEqualTo(stale.ceiling()));
        for (int i = 0; i < 20; i++) {
            advance(100);
            issueAll();
        }
        assertThat(next.lastIssued, greaterThan(crashed.lastIssued));
    }

    @Test
    void takeoverAfterReleaseStartsAboveTheLastIssued() {
        Process first = start(0, 0);
        for (int i = 0; i < 5; i++) {
            advance(100);
            issueAll();
        }
        long ceiling = first.keeper.current().ceiling();
        first.keeper.close(first.lastIssued);
        first.dead = true;
        Process next = start(1, 0);
        SlotBinding taken = next.keeper.current();
        assertThat(taken.slot(), is(0));
        assertThat("the release lets the next holder start before the old ceiling", taken.floor(), is(first.lastIssued));
        assertThat(taken.floor(), greaterThan(0L));
        assertThat(taken.floor() < ceiling, is(true));
        advance(1);
        issueAll();
        assertThat(next.lastIssued, greaterThan(first.lastIssued));
    }

    @Test
    void randomCrashesReleasesAndSkewsNeverIssueATimestampTwice() {
        long[] offsets = {-2_000, 0, 1_500, -300, 700};
        for (long seed = 1; seed <= 30; seed++) {
            processes.clear();
            issued.clear();
            Random random = new Random(seed);
            int nextId = 0;
            for (int step = 0; step < 400; step++) {
                advance(random.nextInt(1_500));
                for (Process process : processes) {
                    if (process.dead) {
                        continue;
                    }
                    if (process.renewing) {
                        process.keeper.runDue();
                        int action = random.nextInt(100);
                        if (action < 3) {
                            process.renewing = false;
                        } else if (action < 6) {
                            process.keeper.close(process.lastIssued);
                            process.dead = true;
                        }
                    } else if (random.nextInt(100) < 5) {
                        process.dead = true;
                    }
                }
                issueAll();
                long alive = processes.stream().filter(p -> !p.dead).count();
                if (alive < 3) {
                    try {
                        start(nextId, offsets[nextId % offsets.length]);
                    } catch (CosIdException exhausted) {
                        // Every slot is held; try again on a later step.
                    }
                    nextId++;
                }
            }
            assertThat("seed " + seed + " issued on both slots", issued.size(), is(2));
        }
    }
}
