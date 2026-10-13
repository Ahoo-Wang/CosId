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

package me.ahoo.cosid.proxy.server.error;

import static me.ahoo.cosid.proxy.api.ErrorResponse.MACHINE_ID_LOST;
import static me.ahoo.cosid.proxy.api.ErrorResponse.MACHINE_ID_OVERFLOW;
import static me.ahoo.cosid.proxy.api.ErrorResponse.NOT_FOUND_MACHINE_STATE;
import static me.ahoo.cosid.proxy.api.ErrorResponse.SEGMENT_ROLLBACK;
import static me.ahoo.cosid.proxy.api.ErrorResponse.TOO_MANY_DISTRIBUTORS;

import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.NotFoundMachineStateException;
import me.ahoo.cosid.proxy.api.ErrorResponse;
import me.ahoo.cosid.segment.IdSegmentRollbackException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Global Rest Exception Handler.
 *
 * @author ahoo wang
 */
@Component
@RestControllerAdvice
@Slf4j
public class GlobalRestExceptionHandler {
    static final String INTERNAL_ERROR = "Internal error";
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleClientException(IllegalArgumentException ex) {
        if (log.isInfoEnabled()) {
            log.info(ex.getMessage(), ex);
        }
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.badRequest(ex.getMessage()));
    }
    
    @ExceptionHandler(MachineIdOverflowException.class)
    public ResponseEntity<ErrorResponse> handleCosIdException(MachineIdOverflowException ex) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.of(MACHINE_ID_OVERFLOW, ex.getMessage()));
    }
    
    @ExceptionHandler(NotFoundMachineStateException.class)
    public ResponseEntity<ErrorResponse> handleCosIdException(NotFoundMachineStateException ex) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.of(NOT_FOUND_MACHINE_STATE, ex.getMessage()));
    }
    
    @ExceptionHandler(MachineIdLostException.class)
    public ResponseEntity<ErrorResponse> handleCosIdException(MachineIdLostException ex) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.of(MACHINE_ID_LOST, ex.getMessage()));
    }
    
    /**
     * The segment max id moved backwards: a conflict with the store's state, not a server bug.
     */
    @ExceptionHandler(IdSegmentRollbackException.class)
    public ResponseEntity<ErrorResponse> handleSegmentRollback(IdSegmentRollbackException ex) {
        if (log.isWarnEnabled()) {
            log.warn(ex.getMessage(), ex);
        }
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(SEGMENT_ROLLBACK, ex.getMessage()));
    }
    
    /**
     * Another segment distributor would exceed {@code cosid.proxy.server.segment.max-distributors}.
     */
    @ExceptionHandler(TooManyDistributorsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyDistributors(TooManyDistributorsException ex) {
        if (log.isWarnEnabled()) {
            log.warn(ex.getMessage());
        }
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse.of(TOO_MANY_DISTRIBUTORS, ex.getMessage()));
    }
    
    /**
     * A request body or parameter that failed validation, as WebFlux reports it.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({WebExchangeBindException.class})
    public ErrorResponse argumentExceptionHandler(WebExchangeBindException e) {
        BindingResult exceptions = e.getBindingResult();
        List<ObjectError> allErrors = exceptions.getAllErrors();
        if (allErrors.isEmpty()) {
            return ErrorResponse.badRequest("Bad request parameter.");
        }
        List<ArgumentError> argumentErrors = allErrors
            .stream()
            .map(objectError -> objectError instanceof FieldError fieldError
                ? new ArgumentError(fieldError.getField(), fieldError.getDefaultMessage())
                : new ArgumentError(objectError.getObjectName(), objectError.getDefaultMessage()))
            .collect(Collectors.toList());
        
        return ErrorResponse.badRequest(argumentErrors);
    }
    
    /**
     * Keeps the status of a framework error, such as {@code 404}, {@code 405} or {@code 415}: as a {@code 500} it could
     * be retried by a gateway, replaying a request that changes state.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        if (log.isInfoEnabled()) {
            log.info(ex.getMessage());
        }
        String reason = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
        return ResponseEntity
            .status(ex.getStatusCode())
            // Such as Allow of a 405, and Accept of a 415.
            .headers(ex.getHeaders())
            .body(ErrorResponse.of(String.valueOf(ex.getStatusCode().value()), reason));
    }
    
    /**
     * Any other error: logged with its details, which do not reach the client.
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleAll(Exception ex) {
        if (log.isErrorEnabled()) {
            log.error(ex.getMessage(), ex);
        }
        return ResponseEntity
            .internalServerError()
            .body(ErrorResponse.unknown(INTERNAL_ERROR));
    }
}
