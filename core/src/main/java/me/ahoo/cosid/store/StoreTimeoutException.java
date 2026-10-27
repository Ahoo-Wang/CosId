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

package me.ahoo.cosid.store;

import me.ahoo.cosid.CosIdException;

/**
 * A store operation did not complete within {@link StoreConfig#timeout()}: the backend's native timeout fired
 * (06-stores.md §1). Its outcome is unknown, as with any failed call: a reserve or acquire may have been applied.
 *
 * <p>Stores throw it, or carry it among the causes of their own exception (one that keeps a status, say); the runtime
 * looks for it through the causes and reports {@link me.ahoo.cosid.observe.GeneratorListener#onStoreTimeout}.
 */
public class StoreTimeoutException extends CosIdException {
    public StoreTimeoutException(String message) {
        super(message);
    }

    public StoreTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Whether a failure is a store timeout: a {@code StoreTimeoutException} itself or among its causes.
     *
     * @param error the failure
     * @return whether it timed out
     */
    public static boolean isTimeout(Throwable error) {
        // Bounded, so that a cause cycle cannot loop.
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 32; depth++) {
            if (cause instanceof StoreTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
