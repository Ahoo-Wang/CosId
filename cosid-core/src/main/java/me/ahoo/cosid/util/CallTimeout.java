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

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounds how long a caller waits for a blocking backend call that has no timeout of its own.
 *
 * <p>When enabled, the call runs on a daemon worker and the caller gives up after {@link #getTimeout()}, interrupting the
 * worker and throwing {@link CosIdException}. A call that timed out may still have been applied by the backend.
 * At most {@link #MAX_CONCURRENT_CALLS} calls run at once; beyond that, callers fail fast instead of piling up threads
 * behind a hung backend.</p>
 *
 * @author ahoo wang
 */
public final class CallTimeout {
    /**
     * No timeout: calls run on the caller thread.
     */
    public static final CallTimeout NONE = new CallTimeout(Duration.ZERO);
    public static final int MAX_CONCURRENT_CALLS = 64;
    
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(0, MAX_CONCURRENT_CALLS,
        60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
        new ThreadFactoryBuilder()
            .setNameFormat("CosId-Call-%d")
            .setDaemon(true)
            .build());
    
    private final Duration timeout;
    
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
        Preconditions.checkArgument(!timeout.isNegative(), "timeout:[%s] can not be negative!", timeout);
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
            throw new CosIdException("Too many calls in flight (max " + MAX_CONCURRENT_CALLS + "), rejected [" + operation + "].",
                rejectedExecutionException);
        }
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            throw new CosIdException("[" + operation + "] timed out after " + timeout + ".", timeoutException);
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
