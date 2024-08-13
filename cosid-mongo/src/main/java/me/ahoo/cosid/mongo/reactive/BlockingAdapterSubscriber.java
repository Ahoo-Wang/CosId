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

package me.ahoo.cosid.mongo.reactive;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.SignalType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BlockingAdapterSubscriber<T> extends BaseSubscriber<T> {
    private final CountDownLatch latch;

    private T value;
    private Throwable error;

    public BlockingAdapterSubscriber() {
        this.latch = new CountDownLatch(1);
    }

    @Override
    protected void hookOnNext(final T value) {
        this.value = value;
    }

    @Override
    protected void hookOnError(Throwable throwable) {
        this.error = throwable;
    }

    public T getValue() {
        return value;
    }

    public T block(final long timeout, final TimeUnit unit) throws InterruptedException, TimeoutException {
        return await(timeout, unit).getValue();
    }

    public Throwable getError() {
        return error;
    }

    @Override
    protected void hookFinally(final SignalType type) {
        latch.countDown();
    }

    public BlockingAdapterSubscriber<T> await(final long timeout, final TimeUnit unit) throws TimeoutException, InterruptedException {
        if (!latch.await(timeout, unit)) {
            throw new TimeoutException("Timeout after " + timeout + " " + unit);
        }
        if (getError() != null) {
            throw new RuntimeException(getError());
        }
        return this;
    }
}
