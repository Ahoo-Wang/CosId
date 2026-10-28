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

import me.ahoo.cosid.GeneratorDisabledException;
import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.store.StoreTimeoutException;
import me.ahoo.cosid.store.remote.RemoteApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Every error as {@code {code, message}} (08-remote.md §2), with the status the client maps back to the primitive's
 * outcome ({@link RemoteApi}). Failures of the backing store answer a generic message and leave their details (hosts,
 * cluster descriptions) to the log.
 */
@RestControllerAdvice
class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(Throwable.class)
    ResponseEntity<Bodies.ErrorBody> handle(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return switch (cause) {
            case ApiException api when api.status() == HttpStatus.SERVICE_UNAVAILABLE -> ResponseEntity.status(api.status())
                .header(HttpHeaders.RETRY_AFTER, "1").body(new Bodies.ErrorBody(api.code(), api.getMessage()));
            case ApiException api -> body(api.status(), api.code(), api.getMessage());
            case Throwable timeout when StoreTimeoutException.isTimeout(timeout) -> {
                log.warn("The backing store timed out", timeout);
                yield body(HttpStatus.GATEWAY_TIMEOUT, RemoteApi.STORE_TIMEOUT, "The backing store timed out");
            }
            case RangeSealedException sealed -> body(HttpStatus.GONE, RemoteApi.RANGE_SEALED, sealed.getMessage());
            case RangeExhaustedException exhausted -> body(HttpStatus.UNPROCESSABLE_CONTENT, RemoteApi.RANGE_EXHAUSTED, exhausted.getMessage());
            case NamespaceMismatchException mismatch -> body(HttpStatus.CONFLICT, RemoteApi.NAMESPACE_MISMATCH, mismatch.getMessage());
            case GeneratorDisabledException disabled -> body(HttpStatus.CONFLICT, RemoteApi.GENERATOR_DISABLED, disabled.getMessage());
            case HttpMessageNotReadableException unreadable -> body(HttpStatus.BAD_REQUEST, RemoteApi.BAD_REQUEST, "The request body is not the expected JSON");
            case MethodArgumentTypeMismatchException mismatch -> body(HttpStatus.BAD_REQUEST, RemoteApi.BAD_REQUEST, mismatch.getName() + " has the wrong type");
            case MissingRequestValueException missing -> body(HttpStatus.BAD_REQUEST, RemoteApi.BAD_REQUEST, missing.getMessage());
            case HttpMediaTypeNotSupportedException unsupported -> body(HttpStatus.BAD_REQUEST, RemoteApi.BAD_REQUEST, "The request body must be JSON");
            case HttpRequestMethodNotSupportedException method -> body(HttpStatus.METHOD_NOT_ALLOWED, RemoteApi.METHOD_NOT_ALLOWED, method.getMessage());
            case NoResourceFoundException missing -> body(HttpStatus.NOT_FOUND, RemoteApi.NOT_FOUND, "No such path: /" + missing.getResourcePath());
            case IllegalArgumentException invalid -> {
                log.info("The backing store rejected a request", invalid);
                yield body(HttpStatus.BAD_REQUEST, RemoteApi.BAD_REQUEST, "The backing store rejected the request");
            }
            case UnsupportedOperationException unsupported -> {
                log.info("The backing store does not support a call", unsupported);
                yield body(HttpStatus.NOT_IMPLEMENTED, RemoteApi.UNSUPPORTED, "The backing store does not support this call");
            }
            default -> {
                log.warn("The backing store failed", cause);
                yield body(HttpStatus.INTERNAL_SERVER_ERROR, RemoteApi.STORE_ERROR, "The backing store failed");
            }
        };
    }

    private static ResponseEntity<Bodies.ErrorBody> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new Bodies.ErrorBody(code, message));
    }
}
