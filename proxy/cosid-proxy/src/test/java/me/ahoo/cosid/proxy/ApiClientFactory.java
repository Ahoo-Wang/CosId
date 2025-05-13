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

package me.ahoo.cosid.proxy;

import me.ahoo.cosid.proxy.api.MachineClient;
import me.ahoo.cosid.proxy.api.SegmentClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

abstract class  ApiClientFactory {

    private static <T> T createApiClient(String proxyHost, Class<T> apiClass) {
        RestClient restClient = RestClient.builder().baseUrl(proxyHost).build();
        RestClientAdapter exchangeAdapter = RestClientAdapter.create(restClient);
        return HttpServiceProxyFactory.builderFor(exchangeAdapter).build().createClient(apiClass);
    }

    static MachineClient createMachineClient(String proxyHost) {
        return createApiClient(proxyHost, MachineClient.class);
    }

    static SegmentClient createSegmentClient(String proxyHost) {
        return createApiClient(proxyHost, SegmentClient.class);
    }
}
