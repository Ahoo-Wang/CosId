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

import me.ahoo.cosid.time.TimeSource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * A slot the user keeps unique (manual or StatefulSet ordinal), with a time window from a local file
 * (05-machine-lease.md §4.4): the file holds the ceiling, written ahead and fsynced before use, so a crash followed by
 * a clock regression cannot repeat ids. Without a file there is no regression protection.
 */
public final class StaticSlot implements SlotHandle, AutoCloseable {
    private final int slot;
    private final int bits;
    private final Optional<Path> stateFile;
    private final Duration ahead;
    private final Optional<Instant> notBefore;
    private final Executor executor;
    private final TimeSource time;

    /**
     * A static slot; {@link #start()} reads the window.
     *
     * @param slot      the slot, in {@code [0, 2^bits)}
     * @param bits      the slot bits
     * @param stateFile the window file; none means no clock regression protection
     * @param ahead     how far ahead the ceiling is written; default 10s
     * @param notBefore the floor when the file does not exist yet (given by the migration tool)
     * @param executor  runs the asynchronous rewrites of the ceiling
     * @param time      the time source
     */
    public StaticSlot(int slot, int bits, Optional<Path> stateFile, Duration ahead, Optional<Instant> notBefore,
                      Executor executor, TimeSource time) {
        if (bits < 1 || bits > Integer.SIZE - 2) {
            throw new IllegalArgumentException("bits must be in [1, " + (Integer.SIZE - 2) + "]: " + bits);
        }
        if (slot < 0 || slot >= 1 << bits) {
            throw new IllegalArgumentException("slot must be in [0, " + (1 << bits) + "): " + slot);
        }
        this.slot = slot;
        this.bits = bits;
        this.stateFile = Objects.requireNonNull(stateFile, "stateFile");
        this.ahead = Objects.requireNonNull(ahead, "ahead");
        this.notBefore = Objects.requireNonNull(notBefore, "notBefore");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.time = Objects.requireNonNull(time, "time");
    }

    public int slot() {
        return slot;
    }

    public int bits() {
        return bits;
    }

    /**
     * Reads the floor from the file and writes the first ceiling ahead.
     */
    public void start() {
        throw new UnsupportedOperationException("R1-C");
    }

    @Override
    public SlotBinding current() {
        throw new UnsupportedOperationException("R1-C");
    }

    /**
     * Writes the exact last issued timestamp as the ceiling, so the next start begins right after it.
     *
     * @param lastIssued the largest timestamp issued on the slot, in milliseconds
     */
    public void close(long lastIssued) {
        throw new UnsupportedOperationException("R1-C");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("R1-C");
    }
}
