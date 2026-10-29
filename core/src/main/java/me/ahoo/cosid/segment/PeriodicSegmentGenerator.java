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

package me.ahoo.cosid.segment;

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.GroupedIdGenerator;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.time.TimeSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Segments grouped by period (D5, 04-segment.md §3): at most two {@link SegmentGenerator}s, the current period's and
 * the next one's, at the store keys {@code {key}/{period}}.
 *
 * <p>The hot path reads the time once and compares it with a precomputed instant; before it, the id comes from the
 * current period's generator. From {@link PeriodicPolicy#prewarm()} before the period ends the next period's generator
 * is created and starts its first reservation, so that crossing the boundary needs no synchronous store call. At the
 * boundary the old period's generator is closed; its key is sealed only {@link PeriodicPolicy#sealAfter()} after the
 * period ended, on the refill executor. Periods only move forward: a generator never returns to a period it left, and a
 * sealed key fails with {@link me.ahoo.cosid.RangeSealedException} rather than being recreated (S6).
 *
 * <p>Its public constructors are the assembly entry {@code CosIdRuntime} uses (R2-core-03): they keep their signatures,
 * and a new parameter comes only through a builder or a static factory, never as one more positional overload
 * (api-freeze.md §4.2).
 */
public final class PeriodicSegmentGenerator implements GroupedIdGenerator, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(PeriodicSegmentGenerator.class);
    private static final Active CLOSED = new Active("", null, Long.MIN_VALUE);

    private final String name;
    private final String key;
    private final String storeName;
    private final Period period;
    private final ZoneId zone;
    private final RangeStore store;
    private final BatchPolicy policy;
    private final PeriodicPolicy periodic;
    private final Executor refillExecutor;
    private final TimeSource time;
    private final GeneratorListener listener;
    private final long prewarmMillis;
    private final long sealAfterMillis;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile Active active;

    // Guarded by lock.
    private long endMillis;
    private Active next;
    private final ArrayDeque<PendingSeal> seals = new ArrayDeque<>();

    /**
     * A periodic segment generator. It reserves nothing until the first {@link #generate()}.
     *
     * @param name           the generator name, for events and errors
     * @param key            the range key the period keys are appended to
     * @param period         the period
     * @param zone           the time zone the periods follow
     * @param store          the range store
     * @param policy         the batch policy of each period
     * @param periodic       the periodic policy
     * @param refillExecutor runs the reservations
     * @param time           the time source
     * @param listener       the event listener
     */
    public PeriodicSegmentGenerator(String name, String key, Period period, ZoneId zone, RangeStore store,
                                    BatchPolicy policy, PeriodicPolicy periodic, Executor refillExecutor,
                                    TimeSource time, GeneratorListener listener) {
        this(name, key, "", period, zone, store, policy, periodic, refillExecutor, time, listener);
    }

    /**
     * A periodic segment generator on a named store. It reserves nothing until the first {@link #generate()}.
     *
     * @param name           the generator name, for events and errors
     * @param key            the range key the period keys are appended to
     * @param storeName      the store name, for {@link GeneratorListener#onStoreTimeout} when a caller waits for a
     *                       refill longer than the reserve timeout
     * @param period         the period
     * @param zone           the time zone the periods follow
     * @param store          the range store
     * @param policy         the batch policy of each period
     * @param periodic       the periodic policy
     * @param refillExecutor runs the reservations
     * @param time           the time source
     * @param listener       the event listener
     */
    public PeriodicSegmentGenerator(String name, String key, String storeName, Period period, ZoneId zone, RangeStore store,
                                    BatchPolicy policy, PeriodicPolicy periodic, Executor refillExecutor,
                                    TimeSource time, GeneratorListener listener) {
        this.name = Objects.requireNonNull(name, "name");
        this.key = Objects.requireNonNull(key, "key");
        this.storeName = Objects.requireNonNull(storeName, "storeName");
        this.period = Objects.requireNonNull(period, "period");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.store = Objects.requireNonNull(store, "store");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.periodic = Objects.requireNonNull(periodic, "periodic");
        this.refillExecutor = Objects.requireNonNull(refillExecutor, "refillExecutor");
        this.time = Objects.requireNonNull(time, "time");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.prewarmMillis = periodic.prewarm().toMillis();
        this.sealAfterMillis = periodic.sealAfter().map(Duration::toMillis).orElse(-1L);
        // The first generate() opens the current period.
        this.active = new Active("", null, Long.MIN_VALUE + 1);
    }

    /**
     * The generator name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Issues the next id of the current period.
     *
     * @return the id with its period
     * @throws me.ahoo.cosid.RangeSealedException      if the period's key is already sealed (a lagging clock)
     * @throws me.ahoo.cosid.RangeUnavailableException if the period's generator has no range in time
     * @throws IllegalStateException                   if the generator is closed
     */
    @Override
    public GroupedId generate() {
        while (true) {
            long now = time.currentMillis();
            Active current = active;
            if (now >= current.slowAtMillis()) {
                current = roll(now);
            }
            try {
                return new GroupedId(current.period(), current.generator().generate());
            } catch (IllegalStateException e) {
                // The period ended and its generator was closed meanwhile: retry in the period that follows.
                if (!current.generator().isClosed() || active == CLOSED) {
                    throw e;
                }
            }
        }
    }

    /**
     * Moves to the period of {@code now} if the current one ended, warms up the next period once its prewarm time
     * came, and seals the keys that are due.
     */
    private Active roll(long now) {
        List<SegmentGenerator> ended = new ArrayList<>(2);
        SegmentGenerator warm = null;
        int dueSeals = 0;
        String[] sealKeys = null;
        Active current;
        lock.lock();
        try {
            current = active;
            if (current == CLOSED) {
                throw new IllegalStateException("Periodic segment generator [" + name + "] is closed");
            }
            if (current.generator() == null || now >= endMillis) {
                current = open(current, now, ended);
            }
            if (next == null && now >= endMillis - prewarmMillis) {
                String nextPeriod = period.keyOf(Instant.ofEpochMilli(endMillis), zone);
                if (!nextPeriod.equals(current.period())) {
                    next = new Active(nextPeriod, newGenerator(nextPeriod), Long.MAX_VALUE);
                    warm = next.generator();
                }
            }
            while (!seals.isEmpty() && now >= seals.peek().atMillis()) {
                if (sealKeys == null) {
                    sealKeys = new String[seals.size()];
                }
                sealKeys[dueSeals++] = seals.poll().key();
            }
            long slowAt = next == null ? endMillis - prewarmMillis : endMillis;
            if (!seals.isEmpty()) {
                slowAt = Math.min(slowAt, seals.peek().atMillis());
            }
            current = new Active(current.period(), current.generator(), slowAt);
            active = current;
            // Closed once the new period is published, so a caller that meets a closed generator finds the new one.
            ended.forEach(SegmentGenerator::close);
        } finally {
            lock.unlock();
        }
        if (warm != null) {
            warm.prefetch();
        }
        for (int i = 0; i < dueSeals; i++) {
            seal(sealKeys[i]);
        }
        return current;
    }

    /**
     * Opens the period of {@code now}: the prewarmed next period if it is that one, otherwise a new generator. The new
     * period's end is computed and checked before any state changes, so a faulty custom {@link Period} fails every call
     * the same way and leaves nothing behind (no generator, no pending seal).
     */
    private Active open(Active current, long now, List<SegmentGenerator> ended) {
        String periodKey = period.keyOf(Instant.ofEpochMilli(now), zone);
        if (current.generator() != null && periodKey.equals(current.period())) {
            // A custom period whose endOf is earlier than the instant keyOf mapped here: keep the period.
            endMillis = Math.max(endMillis, period.endOf(periodKey, zone).toEpochMilli());
            return current;
        }
        long end = period.endOf(periodKey, zone).toEpochMilli();
        if (end <= now) {
            throw new IllegalStateException("Period [" + period + "] maps " + Instant.ofEpochMilli(now) + " to [" + periodKey + "], which ends at "
                + Instant.ofEpochMilli(end));
        }
        Active opened;
        if (next != null && next.period().equals(periodKey)) {
            opened = next;
        } else {
            if (next != null) {
                ended.add(next.generator());
            }
            opened = new Active(periodKey, newGenerator(periodKey), Long.MAX_VALUE);
        }
        next = null;
        if (current.generator() != null) {
            ended.add(current.generator());
            if (sealAfterMillis >= 0) {
                seals.add(new PendingSeal(storeKey(current.period()), saturatedAdd(endMillis, sealAfterMillis)));
            }
        }
        endMillis = end;
        log.debug("Generator [{}] - period [{}] until {}", name, periodKey, Instant.ofEpochMilli(end));
        return opened;
    }

    private SegmentGenerator newGenerator(String periodKey) {
        return new SegmentGenerator(name, storeKey(periodKey), storeName, store, policy, refillExecutor, time, listener);
    }

    private String storeKey(String periodKey) {
        return key + "/" + periodKey;
    }

    private void seal(String sealKey) {
        try {
            refillExecutor.execute(() -> {
                try {
                    boolean sealed = store.seal(sealKey);
                    log.debug("Generator [{}] - sealed key [{}]: {}", name, sealKey, sealed);
                } catch (RuntimeException e) {
                    log.warn("Generator [{}] - sealing key [{}] failed: {}", name, sealKey, e.toString());
                }
            });
        } catch (RuntimeException rejected) {
            log.warn("Generator [{}] - sealing key [{}] not started: {}", name, sealKey, rejected.toString());
        }
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    /**
     * The period being issued, for {@code describe()}.
     *
     * @return the period key, empty before the first id
     */
    public String currentPeriod() {
        return active.period();
    }

    /**
     * The values at hand in the period being issued, for {@code describe()}.
     *
     * @return the rest of its current range plus its ready ranges; 0 before the first id
     */
    public long held() {
        SegmentGenerator generator = active.generator();
        return generator == null ? 0 : Math.max(0, generator.held());
    }

    /**
     * The size of the next reservation in the period being issued, for {@code describe()}.
     *
     * @return the batch size the rate estimate gives now; the step before the first id
     */
    public long batch() {
        SegmentGenerator generator = active.generator();
        return generator == null ? policy.step() : generator.batch();
    }

    /**
     * The prewarmed next period, if any.
     */
    SegmentGenerator nextGenerator() {
        lock.lock();
        try {
            return next == null ? null : next.generator();
        } finally {
            lock.unlock();
        }
    }

    PeriodicPolicy periodic() {
        return periodic;
    }

    /**
     * Closes the generators of the current and the next period. Keys not sealed yet stay unsealed. Closing again does
     * nothing.
     */
    @Override
    public void close() {
        lock.lock();
        try {
            Active current = active;
            if (current == CLOSED) {
                return;
            }
            active = CLOSED;
            if (current.generator() != null) {
                current.generator().close();
            }
            if (next != null) {
                next.generator().close();
                next = null;
            }
            seals.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * The period being issued and the time from which the hot path has to take the lock.
     */
    private record Active(String period, SegmentGenerator generator, long slowAtMillis) {
    }

    private record PendingSeal(String key, long atMillis) {
    }
}
