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

package me.ahoo.cosid.proxy.api;

import me.ahoo.coapi.api.CoApi;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

/**
 * The {@link MachineLeaseApi} client of a CosId proxy server.
 */
@CoApi(baseUrl = MachineLeaseClient.BASE_URL)
public interface MachineLeaseClient extends MachineLeaseApi {
    String BASE_URL = "${cosid.proxy.host:http://localhost:8688}";
    
    /**
     * Sends {@code GET time?namespace=...}: the HTTP client proxy sends the request of an annotated default method and
     * does not run its body. A server before 4.1 ignores the parameter.
     *
     * @since 4.1.0
     */
    @Override
    @GetExchange("time")
    default long serverTime(@RequestParam(name = "namespace", required = false) @Nullable String namespace) {
        return serverTime();
    }
}
