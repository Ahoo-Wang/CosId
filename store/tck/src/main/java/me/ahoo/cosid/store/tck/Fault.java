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

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A fault a {@link FaultInjector} applies to one store operation (06-stores.md §3).
 */
public sealed interface Fault {
    /**
     * Waits, then performs the operation: a slow store, or a timeout when longer than the caller waits.
     *
     * @param delay how long to wait first
     */
    record Delay(Duration delay) implements Fault {
        public Delay {
            Objects.requireNonNull(delay, "delay");
        }
    }

    /**
     * Throws without performing the operation: the request never reached the store.
     *
     * @param error the exception to throw
     */
    record Fail(Supplier<? extends RuntimeException> error) implements Fault {
        public Fail {
            Objects.requireNonNull(error, "error");
        }
    }

    /**
     * Performs the operation, then throws instead of returning: the response was lost, and a retry replays a request
     * the store already applied.
     *
     * @param error the exception to throw
     */
    record LoseResponse(Supplier<? extends RuntimeException> error) implements Fault {
        public LoseResponse {
            Objects.requireNonNull(error, "error");
        }
    }
}
