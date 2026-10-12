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

import me.ahoo.cosid.proxy.api.ErrorResponse;
import me.ahoo.cosid.segment.IdSegmentRollbackException;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.WebExchangeBindException;

class GlobalRestExceptionHandlerTest {
    
    @RestController
    static class Failing {
        @GetMapping("/rollback")
        String rollback() {
            throw new IdSegmentRollbackException("ns.name", 10, 20);
        }
        
        @GetMapping("/illegal-state")
        String illegalState() {
            throw new IllegalStateException("executor has been shut down");
        }
        
        @GetMapping("/illegal-argument")
        String illegalArgument() {
            throw new IllegalArgumentException("step:[0] must be greater than 0!");
        }
        
        @PostMapping("/post-only")
        String postOnly() {
            return "ok";
        }
        
        @GetMapping("/bind")
        String bind() throws NoSuchMethodException {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            bindingResult.addError(new FieldError("request", "step", "must be positive"));
            bindingResult.addError(new ObjectError("request", "offset and step overflow"));
            MethodParameter parameter = new MethodParameter(Failing.class.getDeclaredMethod("bind"), -1);
            throw new WebExchangeBindException(parameter, bindingResult);
        }
    }
    
    private final WebTestClient client = WebTestClient.bindToController(new Failing())
        .controllerAdvice(new GlobalRestExceptionHandler())
        .build();
    
    @Test
    void segmentRollbackIsConflict() {
        client.get().uri("/rollback").exchange()
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.code").isEqualTo(ErrorResponse.SEGMENT_ROLLBACK);
    }
    
    @Test
    void otherIllegalStatesStayServerErrorsWithoutTheirDetails() {
        client.get().uri("/illegal-state").exchange()
            .expectStatus().is5xxServerError()
            .expectBody().jsonPath("$.msg").isEqualTo(GlobalRestExceptionHandler.INTERNAL_ERROR);
    }
    
    @Test
    void illegalArgumentIsBadRequest() {
        client.get().uri("/illegal-argument").exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo(ErrorResponse.BAD_REQUEST)
            .jsonPath("$.msg").isEqualTo("step:[0] must be greater than 0!");
    }
    
    @Test
    void frameworkErrorsKeepTheirStatus() {
        client.get().uri("/post-only").exchange()
            .expectStatus().isEqualTo(405)
            .expectBody().jsonPath("$.code").isEqualTo("405");
        client.get().uri("/missing").exchange()
            .expectStatus().isNotFound();
    }
    
    @Test
    void bindErrorsListTheirFields() {
        client.get().uri("/bind").exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo(ErrorResponse.BAD_REQUEST)
            .jsonPath("$.errors[0].field").isEqualTo("step")
            .jsonPath("$.errors[1].field").isEqualTo("request");
    }
}
