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

package me.ahoo.cosid.machine;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * Represents the state of a machine ID guardian operation.
 *
 * <p>This class encapsulates the result of a guarding attempt for a specific machine ID,
 * including the timestamp of the operation and any error that occurred.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Successful guard operation
 * GuardianState successState = GuardianState.success(System.currentTimeMillis());
 *
 * // Failed guard operation
 * GuardianState failedState = GuardianState.failed(System.currentTimeMillis(), new RuntimeException("Guard failed"));
 *
 * if (failedState.isFailed()) {
 *     // Handle failure
 * }
 * }</pre>
 *
 * @author ahoo wang
 */
@AllArgsConstructor
@Data
public class GuardianState {
    /**
     * The initial state representing no guarding operation has been performed.
     */
    public static final GuardianState INITIAL = new GuardianState(0, null);

    /**
     * The timestamp (in milliseconds since epoch) when the guarding operation was performed.
     */
    private final long guardAt;

    /**
     * The error that occurred during the guarding operation, or null if successful.
     */
    @Nullable
    private final Throwable error;

    /**
     * Checks if the guarding operation failed.
     *
     * @return true if an error occurred during guarding, false otherwise
     */
    public boolean isFailed() {
        return error != null;
    }

    /**
     * Creates a successful guardian state.
     *
     * @param guardAt the timestamp of the successful guarding operation
     * @return a new GuardianState representing success
     */
    static GuardianState success(final long guardAt) {
        return new GuardianState(guardAt, null);
    }

    /**
     * Creates a failed guardian state.
     *
     * @param guardAt the timestamp of the failed guarding operation
     * @param error   the error that caused the failure
     * @return a new GuardianState representing failure
     */
    static GuardianState failed(final long guardAt, final Throwable error) {
        return new GuardianState(guardAt, error);
    }
}
