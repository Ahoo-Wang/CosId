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

/**
 * Thrown when a backend call is not started because {@link CallTimeout#getMaxConcurrentCalls()} calls are already
 * running, usually behind a slow or hung backend. Nothing was sent to the backend, so the call can be retried later.
 *
 * @author ahoo wang
 */
public class CallRejectedException extends CosIdException {
    
    public CallRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
