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

package me.ahoo.cosid.spring.redis;

import me.ahoo.cosid.CosIdException;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Bounds how long a caller waits for a Redis command.
 *
 * <p>{@link org.springframework.data.redis.core.StringRedisTemplate} has no per-call timeout: a blocked command waits
 * for the connection factory's command timeout (60s by default for Lettuce). Machine id guarding runs on a single
 * thread, so one hung command stalls it. When enabled, the command runs on a daemon worker and the caller gives up
 * after {@link #getTimeout()}, interrupting the worker and throwing {@link RedisCommandTimeoutException}.</p>
 *
 * <p>It bounds the steady-state commands (machine id guard and revert, segment {@code nextMaxId}). The first command
 * at startup (machine id distribution, segment offset initialization) is left unbounded: on a cold JVM it pays for
 * connecting and class loading, which can take about a second even against a local Redis.</p>
 *
 * <p>A timed-out command may still have been applied on the server. That is safe for CosId's commands: a lost
 * segment increment only leaves a gap, and machine id distribution is idempotent per instance.</p>
 *
 * @author ahoo wang
 */
public final class RedisCommandTimeout {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(1);
    /**
     * No timeout: commands run on the caller thread, as before.
     */
    public static final RedisCommandTimeout NONE = new RedisCommandTimeout(Duration.ZERO);

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
        .setNameFormat("CosId-Redis-Command-%d")
        .setDaemon(true)
        .build());

    private final Duration timeout;

    private RedisCommandTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * Create a command timeout.
     *
     * @param timeout timeout of each command, {@code null} or zero means no timeout
     * @return command timeout
     */
    public static RedisCommandTimeout of(Duration timeout) {
        if (timeout == null || timeout.isZero()) {
            return NONE;
        }
        Preconditions.checkArgument(!timeout.isNegative(), "timeout:[%s] can not be negative!", timeout);
        return new RedisCommandTimeout(timeout);
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isEnabled() {
        return !timeout.isZero();
    }

    /**
     * Run a Redis command within the timeout.
     *
     * @param operation name of the operation, used in the timeout message
     * @param command   the Redis command
     * @param <T>       result type
     * @return the command result
     */
    public <T> T call(String operation, Supplier<T> command) {
        if (!isEnabled()) {
            return command.get();
        }
        Future<T> future = EXECUTOR.submit(command::get);
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            throw new RedisCommandTimeoutException(operation, timeout, timeoutException);
        } catch (InterruptedException interruptedException) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CosIdException("Interrupted while waiting for Redis operation [" + operation + "].", interruptedException);
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new CosIdException(cause);
        }
    }

    public void run(String operation, Runnable command) {
        call(operation, () -> {
            command.run();
            return null;
        });
    }

    @Override
    public String toString() {
        return "RedisCommandTimeout{timeout=" + timeout + '}';
    }
}
