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

package me.ahoo.cosid;

/**
 * CosId root exception for all CosId-related errors.
 * 
 * <p>This is the base exception class for all exceptions thrown by the CosId library.
 * It extends {@link RuntimeException} to indicate that these are typically
 * unrecoverable errors that should be handled at appropriate levels in the application.
 * 
 * <p>Specific exception types in the CosId library extend this class to provide
 * more detailed error information for different failure scenarios, such as:
 * <ul>
 *   <li>ID generation failures</li>
 *   <li>Machine ID distribution problems</li>
 *   <li>Configuration errors</li>
 *   <li>Clock synchronization issues</li>
 * </ul>
 *
 * @author ahoo wang
 */
public class CosIdException extends RuntimeException {

    /**
     * Constructs a new CosId exception with no detail message.
     */
    public CosIdException() {
    }

    /**
     * Constructs a new CosId exception with the specified detail message.
     *
     * @param message the detail message
     */
    public CosIdException(String message) {
        super(message);
    }

    /**
     * Constructs a new CosId exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public CosIdException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new CosId exception with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public CosIdException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new CosId exception with the specified detail message, cause,
     * suppression enabled or disabled, and writable stack trace enabled or disabled.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     * @param enableSuppression whether suppression is enabled or disabled
     * @param writableStackTrace whether the stack trace should be writable
     */
    public CosIdException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
