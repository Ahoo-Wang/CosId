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

package me.ahoo.cosid.store.tck;

import me.ahoo.cosid.CosIdException;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The faults a {@link FaultyRangeStore} or {@link FaultySlotLeaseStore} applies, per operation: one-shot faults in the
 * order they were queued, then the standing fault if any. Thread-safe.
 */
public final class FaultInjector {
    private final Map<Operation, Queue<Fault>> onceFaults = new EnumMap<>(Operation.class);
    private final Map<Operation, AtomicLong> calls = new EnumMap<>(Operation.class);
    private final Map<Operation, Fault> standing = new ConcurrentHashMap<>();


    /**
     * An injector without faults.
     */
    public FaultInjector() {
        for (Operation operation : Operation.values()) {
            onceFaults.put(operation, new ConcurrentLinkedQueue<>());
            calls.put(operation, new AtomicLong());
        }
    }

    /**
     * A store operation.
     */
    public enum Operation {
        RESERVE,
        SEAL,
        ACQUIRE,
        RENEW,
        RELEASE
    }

    /**
     * Applies {@code fault} to the next call of {@code operation} only.
     *
     * @param operation the operation
     * @param fault     the fault
     * @return this injector
     */
    public FaultInjector once(Operation operation, Fault fault) {
        onceFaults.get(Objects.requireNonNull(operation, "operation")).add(Objects.requireNonNull(fault, "fault"));
        return this;
    }

    /**
     * Applies {@code fault} to every call of {@code operation} until {@link #clear()}.
     *
     * @param operation the operation
     * @param fault     the fault
     * @return this injector
     */
    public FaultInjector always(Operation operation, Fault fault) {
        standing.put(Objects.requireNonNull(operation, "operation"), Objects.requireNonNull(fault, "fault"));
        return this;
    }

    /**
     * Removes every fault.
     */
    public void clear() {
        standing.clear();
        onceFaults.values().forEach(Queue::clear);
    }

    /**
     * How many calls of {@code operation} reached the wrapped store.
     *
     * @param operation the operation
     * @return the count
     */
    public long calls(Operation operation) {
        return calls.get(Objects.requireNonNull(operation, "operation")).get();
    }

    /**
     * Runs {@code call} under the next fault of {@code operation}: the wrappers' single path to the wrapped store.
     */
    <T> T apply(Operation operation, Supplier<T> call) {
        Fault fault = onceFaults.get(operation).poll();
        if (fault == null) {
            fault = standing.get(operation);
        }
        if (fault == null) {
            return reach(operation, call);
        }
        return switch (fault) {
            case Fault.Delay delay -> {
                try {
                    Thread.sleep(delay.delay());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CosIdException("Interrupted during an injected delay of " + operation, e);
                }
                yield reach(operation, call);
            }
            case Fault.Fail fail -> throw fail.error().get();
            case Fault.LoseResponse lose -> {
                reach(operation, call);
                throw lose.error().get();
            }
        };
    }

    private <T> T reach(Operation operation, Supplier<T> call) {
        calls.get(operation).incrementAndGet();
        return call.get();
    }
}
