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

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Error Response.
 *
 * @author ahoo wang
 */
public class ErrorResponse {
    
    public static final String BAD_REQUEST = "400";
    
    private final String code;
    private final String msg;
    @Nullable
    private final List<?> errors;
    
    public ErrorResponse(String code, String msg, List<?> errors) {
        this.code = code;
        this.msg = msg;
        this.errors = errors;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMsg() {
        return msg;
    }
    
    public List<?> getErrors() {
        return errors;
    }
    
    public static ErrorResponse of(String code, String msg) {
        return new ErrorResponse(code, msg, Collections.emptyList());
    }
    
    public static ErrorResponse unknown(String msg) {
        return of("500", msg);
    }
    
    public static ErrorResponse badRequest(String msg) {
        return badRequest(msg, null);
    }
    
    public static ErrorResponse badRequest(List<?> errors) {
        return badRequest(null, errors);
    }
    
    public static ErrorResponse badRequest(@Nullable String msg, @Nullable List<?> errors) {
        return new ErrorResponse(BAD_REQUEST, msg, errors);
    }
}
