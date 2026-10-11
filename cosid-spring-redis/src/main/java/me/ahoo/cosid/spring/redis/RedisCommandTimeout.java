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

import me.ahoo.cosid.util.CallTimeout;

import com.google.common.base.Preconditions;

import java.time.Duration;
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

    /**
     * Upper bound of concurrently running commands, shared with the other {@link CallTimeout} users. Beyond it, callers
     * fail fast instead of piling up threads behind a hung Redis.
     */
    public static final int MAX_CONCURRENT_COMMANDS = CallTimeout.MAX_CONCURRENT_CALLS;

    private final Duration timeout;
    private final CallTimeout callTimeout;

    private RedisCommandTimeout(Duration timeout) {
        this.timeout = timeout;
        this.callTimeout = CallTimeout.of(timeout);
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
        return callTimeout.call(operation, command::get,
            (timedOutOperation, timeoutException) -> new RedisCommandTimeoutException(timedOutOperation, timeout, timeoutException));
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
