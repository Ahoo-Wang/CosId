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

/**
 * Represents the status of a machine ID guardian operation.
 *
 * <p>This enum is used to indicate the result of attempting to guard a machine ID
 * for a specific instance. It provides a simple way to track whether the guarding
 * operation succeeded or failed.
 *
 * <p>Example usage:
 * <pre>{@code
 * GuardianStatus status = GuardianStatus.SUCCESS;
 * if (status == GuardianStatus.FAILURE) {
 *     // Handle failure case
 * }
 * }</pre>
 *
 * @author ahoo wang
 */
public enum GuardianStatus {
    /**
     * Indicates that the machine ID guarding operation was successful.
     *
     * <p>This status means the guardian was able to successfully protect or renew
     * the machine ID for the associated instance.
     */
    SUCCESS,

    /**
     * Indicates that the machine ID guarding operation failed.
     *
     * <p>This status means the guardian encountered an error while trying to protect
     * or renew the machine ID for the associated instance. The failure may be due to
     * network issues, permission problems, or other runtime errors.
     */
    FAILURE
}
