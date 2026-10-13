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

package me.ahoo.cosid.proxy.server;

import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.proxy.server.controller.IdController;
import me.ahoo.cosid.proxy.server.controller.NoStoreIdsWebFilter;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Ids are served by {@code GET}: no cache may keep them.
 */
class NoStoreIdsWebFilterTest {
    private final WebTestClient webTestClient = WebTestClient
        .bindToController(new IdController(provider()))
        .webFilter(new NoStoreIdsWebFilter())
        .build();
    
    private static DefaultIdGeneratorProvider provider() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        provider.setShare(MockIdGenerator.INSTANCE);
        provider.set("biz", MockIdGenerator.INSTANCE);
        return provider;
    }
    
    @Test
    void idsAreNotStored() {
        for (String path : new String[] {"/ids", "/ids/biz", "/ids/as-string", "/ids/as-string/biz"}) {
            webTestClient.get().uri(path).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");
        }
    }
}
