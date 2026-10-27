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

package me.ahoo.cosid.server;

import me.ahoo.cosid.store.remote.RemoteApi;

import org.springframework.http.HttpStatus;

/**
 * A request the server answers with an error of its own: the status and the {@code code} of the error body.
 */
final class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient HttpStatus status;
    private final String code;

    ApiException(HttpStatus status, String code, String message) {
        super(message, null, false, false);
        this.status = status;
        this.code = code;
    }

    static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, RemoteApi.BAD_REQUEST, message);
    }

    static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, RemoteApi.FORBIDDEN, message);
    }

    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, RemoteApi.NOT_FOUND, message);
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }
}
