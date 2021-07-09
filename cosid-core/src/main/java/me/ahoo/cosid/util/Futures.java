/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.Thread.currentThread;

/**
 * @author ahoo wang
 */
public final class Futures {
    private Futures() {
    }

    public static <T> T getUnChecked(Future<T> future, Duration timeout) {
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new CosIdException(e);
        } catch (TimeoutException e) {
            throw new CosIdException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CosIdException) {
                throw (CosIdException) e.getCause();
            }
            throw new CosIdException(e);
        }
    }
}
