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

package me.ahoo.cosid.util;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.event.CosIdEvents;
import me.ahoo.cosid.internal.Checks;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

/**
 * Bounds how long a caller waits for a blocking backend call that has no timeout of its own.
 *
 * <p>When enabled, the call runs on a daemon worker and the caller gives up after {@link #getTimeout()}, interrupting the
 * worker and throwing {@link CosIdException}. A call that timed out may still have been applied by the backend.
 * At most {@link #getMaxConcurrentCalls()} calls (by default {@link #MAX_CONCURRENT_CALLS}) run at once, shared by
 * every backend; beyond that, callers fail fast with {@link CallRejectedException} instead of piling up threads behind
 * a hung backend.</p>
 *
 * @author ahoo wang
 */
public final class CallTimeout {
    /**
     * No timeout: calls run on the caller thread.
     */
    public static final CallTimeout NONE = new CallTimeout(Duration.ZERO);
    /**
     * Default limit of calls running at once.
     */
    public static final int MAX_CONCURRENT_CALLS = 64;
    
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(0, MAX_CONCURRENT_CALLS,
        60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
        Checks.daemonThreadFactory("CosId-Call-%d"));
    
    private final Duration timeout;
    
    /**
     * Most calls with a timeout that may run at once, across all backends.
     *
     * @return the limit
     */
    public static int getMaxConcurrentCalls() {
        return EXECUTOR.getMaximumPoolSize();
    }
    
    /**
     * Change the most calls with a timeout that may run at once, across all backends. Calls already running are not
     * affected by a lower limit.
     *
     * @param maxConcurrentCalls the limit, at least 1
     */
    public static void setMaxConcurrentCalls(int maxConcurrentCalls) {
        Checks.checkArgument(maxConcurrentCalls > 0, "maxConcurrentCalls:[%s] must be greater than 0!", maxConcurrentCalls);
        EXECUTOR.setMaximumPoolSize(maxConcurrentCalls);
    }
    
    private CallTimeout(Duration timeout) {
        this.timeout = timeout;
    }
    
    /**
     * Create a call timeout.
     *
     * @param timeout timeout of each call, {@code null} or zero means no timeout
     * @return call timeout
     */
    public static CallTimeout of(Duration timeout) {
        if (timeout == null || timeout.isZero()) {
            return NONE;
        }
        Checks.checkArgument(!timeout.isNegative(), "timeout:[%s] can not be negative!", timeout);
        return new CallTimeout(timeout);
    }
    
    public Duration getTimeout() {
        return timeout;
    }
    
    public boolean isEnabled() {
        return !timeout.isZero();
    }
    
    /**
     * Run a call within the timeout. Runtime exceptions and errors of the call are rethrown as they are, checked
     * exceptions are wrapped in {@link CosIdException}.
     *
     * @param operation name of the operation, used in error messages
     * @param call      the call
     * @param <T>       result type
     * @return the call result
     */
    public <T> T call(String operation, Callable<T> call) {
        return call(operation, call, (timedOutOperation, timeoutException) ->
            new CosIdException("[" + timedOutOperation + "] timed out after " + timeout + ".", timeoutException));
    }
    
    /**
     * Run a call within the timeout, throwing the exception created by {@code timeoutExceptionFactory} on timeout.
     *
     * @param operation               name of the operation, used in error messages
     * @param call                    the call
     * @param timeoutExceptionFactory creates the exception thrown on timeout from the operation and the cause
     * @param <T>                     result type
     * @return the call result
     */
    public <T> T call(String operation, Callable<T> call, BiFunction<String, TimeoutException, ? extends RuntimeException> timeoutExceptionFactory) {
        if (!isEnabled()) {
            try {
                return call.call();
            } catch (RuntimeException runtimeException) {
                throw runtimeException;
            } catch (Exception exception) {
                throw new CosIdException(exception.getMessage(), exception);
            }
        }
        Future<T> future;
        try {
            future = EXECUTOR.submit(call);
        } catch (RejectedExecutionException rejectedExecutionException) {
            CosIdEvents.listener().onCallRejected(operation);
            throw new CallRejectedException("Too many calls in flight (max " + getMaxConcurrentCalls() + "), rejected [" + operation + "].",
                rejectedExecutionException);
        }
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            CosIdEvents.listener().onCallTimeout(operation, timeout);
            throw timeoutExceptionFactory.apply(operation, timeoutException);
        } catch (InterruptedException interruptedException) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CosIdException("Interrupted while waiting for [" + operation + "].", interruptedException);
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new CosIdException(cause.getMessage(), cause);
        }
    }
    
    @Override
    public String toString() {
        return "CallTimeout{timeout=" + timeout + '}';
    }
}
