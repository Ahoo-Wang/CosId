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

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleCoSecException(IllegalArgumentException ex) {
        if (log.isInfoEnabled()) {
            log.info(ex.getMessage(), ex);
        }
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.unknown(ex.getMessage()));
    }
    
    
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ErrorResponse argumentExceptionHandler(MethodArgumentNotValidException e) {
        BindingResult exceptions = e.getBindingResult();
        List<ObjectError> allErrors = exceptions.getAllErrors();
        if (allErrors.isEmpty()) {
            return ErrorResponse.badRequest("Bad request parameter.");
        }
        List<ArgumentError> argumentErrors = allErrors
            .stream()
            .map(objectError -> (FieldError) objectError)
            .map(fieldError -> new ArgumentError(fieldError.getField(), fieldError.getDefaultMessage()))
            .collect(Collectors.toList());
        
        return ErrorResponse.badRequest(argumentErrors);
    }
    
    @ExceptionHandler
    @ResponseStatus
    public ResponseEntity<ErrorResponse> handleAll(Exception ex) {
        if (log.isErrorEnabled()) {
            log.error(ex.getMessage(), ex);
        }
        return ResponseEntity
            .internalServerError()
            .body(ErrorResponse.unknown(ex.getMessage()));
    }
}
