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

package me.ahoo.cosid.store.zookeeper;

import me.ahoo.cosid.CosIdException;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The Curator calls of the store, each one a background operation awaited up to the deadline of the store operation
 * that issued it: Curator has no per-operation timeout of its own (06-stores.md §1, {@code timeout}). A call that times
 * out may still be applied by the server; the stores tolerate that, since every write is a conditional update their
 * retries re-read.
 */
final class Zk {
    private final CuratorFramework client;
    private final long timeoutNanos;

    Zk(CuratorFramework client, Duration timeout) {
        this.client = Objects.requireNonNull(client, "client");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
        this.timeoutNanos = timeout.toNanos();
    }

    CuratorFramework client() {
        return client;
    }

    /**
     * The deadline of a store operation starting now.
     *
     * @return the deadline, in {@link System#nanoTime()}
     */
    long deadline() {
        return System.nanoTime() + timeoutNanos;
    }

    /**
     * Starts a background operation.
     *
     * @param operation starts the operation with the callback
     * @return the event of the operation, completed on Curator's event thread
     */
    CompletableFuture<CuratorEvent> start(Operation operation) {
        CompletableFuture<CuratorEvent> event = new CompletableFuture<>();
        try {
            operation.start((client, completed) -> event.complete(completed));
        } catch (Exception e) {
            event.completeExceptionally(e);
        }
        return event;
    }

    /**
     * Runs a background operation and waits for it up to the deadline.
     *
     * @param what      the operation and path, for the messages
     * @param deadline  the deadline from {@link #deadline()}
     * @param operation starts the operation with the callback
     * @return the event; its result code is the ZooKeeper outcome
     */
    CuratorEvent call(String what, long deadline, Operation operation) {
        return await(what, deadline, start(operation));
    }

    /**
     * Waits for a started operation up to the deadline.
     *
     * @param what     the operation and path, for the messages
     * @param deadline the deadline from {@link #deadline()}
     * @param event    the started operation
     * @return the event
     * @throws CosIdException on timeout, interruption or a failure to start
     */
    CuratorEvent await(String what, long deadline, CompletableFuture<CuratorEvent> event) {
        try {
            return event.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            event.cancel(false);
            throw new CosIdException("ZooKeeper " + what + " timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CosIdException("ZooKeeper " + what + " interrupted", e);
        } catch (ExecutionException e) {
            throw new CosIdException("ZooKeeper " + what + " failed: " + e.getCause(), e.getCause());
        }
    }

    /**
     * The result code of an event.
     *
     * @param event the event
     * @return the code
     */
    static KeeperException.Code code(CuratorEvent event) {
        return KeeperException.Code.get(event.getResultCode());
    }

    /**
     * Fails for a result code the caller does not handle.
     *
     * @param what  the operation and path
     * @param event the event
     * @return never returns normally
     */
    static CosIdException unexpected(String what, CuratorEvent event) {
        KeeperException.Code code = code(event);
        return new CosIdException("ZooKeeper " + what + " failed: " + code, KeeperException.create(code, event.getPath()));
    }

    /**
     * The stat an event carries; its {@code mtime} is the store clock (06 §2.4).
     *
     * @param what  the operation and path
     * @param event an event carrying the stat of the node
     * @return the stat
     */
    static Stat stat(String what, CuratorEvent event) {
        Stat stat = event.getStat();
        if (stat == null) {
            throw new CosIdException("ZooKeeper " + what + " returned no stat");
        }
        return stat;
    }

    /**
     * Pauses after a lost compare-and-set, longer with each attempt and never past the deadline, so contending
     * clients spread out.
     *
     * @param attempt  the attempts lost so far, from 0
     * @param deadline the deadline of the operation
     * @param what     the operation and path
     */
    static void backoff(int attempt, long deadline, String what) {
        if (System.nanoTime() >= deadline) {
            throw new CosIdException("ZooKeeper " + what + " timed out after " + (attempt + 1) + " conflicts");
        }
        if (attempt < 2) {
            return;
        }
        long boundMillis = 1L << Math.min(attempt - 1, 5);
        long pauseNanos = Math.min(TimeUnit.MILLISECONDS.toNanos(ThreadLocalRandom.current().nextLong(boundMillis + 1)),
            Math.max(0, deadline - System.nanoTime()));
        try {
            TimeUnit.NANOSECONDS.sleep(pauseNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CosIdException("ZooKeeper " + what + " interrupted", e);
        }
    }

    /**
     * Starts one Curator background operation, such as
     * {@code callback -> client.getData().inBackground(callback).forPath(path)}.
     */
    @FunctionalInterface
    interface Operation {
        void start(BackgroundCallback callback) throws Exception;
    }
}
