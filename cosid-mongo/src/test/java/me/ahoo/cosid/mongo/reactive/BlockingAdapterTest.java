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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Duration;

class BlockingAdapterTest {
    @Test
    void blockPublisherShouldReturnValue() {
        String result = BlockingAdapter.block((Publisher<String>) Mono.just("test"));
        assertThat(result, equalTo("test"));
    }

    @Test
    void blockMonoShouldReturnValue() {
        String result = BlockingAdapter.block(Mono.just("test"));
        assertThat(result, equalTo("test"));
    }

    @Test
    void blockShouldPropagateRuntimeExceptionInstance() {
        IllegalStateException failure = new IllegalStateException("boom");

        RuntimeException actual = Assertions.assertThrows(RuntimeException.class, () -> BlockingAdapter.block(Mono.error(failure)));

        assertThat(actual, sameInstance(failure));
    }

    @Test
    void blockShouldWrapCheckedFailureAsRuntimeException() {
        Exception failure = new Exception("checked");

        RuntimeException actual = Assertions.assertThrows(RuntimeException.class, () -> BlockingAdapter.block(Mono.error(failure)));

        assertThat(actual.getCause(), sameInstance(failure));
    }

    @Test
    void blockShouldWrapTimeoutException() {
        RuntimeException actual = Assertions.assertThrows(
            RuntimeException.class,
            () -> BlockingAdapter.block(Mono.never(), Duration.ofMillis(10))
        );

        assertThat(actual.getCause(), instanceOf(java.util.concurrent.TimeoutException.class));
    }
}
