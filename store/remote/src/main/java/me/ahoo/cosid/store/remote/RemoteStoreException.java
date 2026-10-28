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

package me.ahoo.cosid.store.remote;

import me.ahoo.cosid.CosIdException;

/**
 * A call to {@code cosid-server} failed: the server answered with an error the primitive has no outcome for (a
 * {@code 401}, {@code 403}, {@code 429}, {@code 503}, {@code 5xx}, ...), or the call itself failed (connection,
 * timeout).
 */
public class RemoteStoreException extends CosIdException {
    private final int status;
    private final String code;

    /**
     * An error the server answered.
     *
     * @param status  the HTTP status
     * @param code    the {@code code} of the error body; {@code null} if the body had none
     * @param message the message
     */
    public RemoteStoreException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * An error the server answered, with a cause on this side, such as the {@link me.ahoo.cosid.store.StoreTimeoutException}
     * of a {@code store_timeout}.
     *
     * @param status  the HTTP status
     * @param code    the {@code code} of the error body
     * @param message the message
     * @param cause   the cause
     */
    public RemoteStoreException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    /**
     * A call that got no answer.
     *
     * @param message the message
     * @param cause   the cause
     */
    public RemoteStoreException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
        this.code = null;
    }

    /**
     * The HTTP status.
     *
     * @return the status; 0 when the server did not answer
     */
    public int status() {
        return status;
    }

    /**
     * The error code ({@link RemoteApi}).
     *
     * @return the code; {@code null} when the server did not answer or sent none
     */
    public String code() {
        return code;
    }

    /**
     * Whether calling again later may succeed: the server was busy ({@code 503}), its store timed out ({@code 504}), or
     * it did not answer.
     *
     * @return whether the failure is transient
     */
    public boolean transientFailure() {
        return status == 0 || status == 503 || status == 504;
    }
}
