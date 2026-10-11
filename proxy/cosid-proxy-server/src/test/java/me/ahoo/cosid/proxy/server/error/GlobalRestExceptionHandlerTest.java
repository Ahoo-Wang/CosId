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
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
    void otherIllegalStatesStayServerErrors() {
        client.get().uri("/illegal-state").exchange()
            .expectStatus().is5xxServerError();
    }
}
