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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import me.ahoo.cosid.proxy.api.MachineLeaseClient;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * The lease store asks the server for the clock of a namespace; the plain time request stays what 4.0 sends.
 */
class ProxyServerTimeTest {
    
    @Test
    void theTimeOfANamespaceAndThePlainTime() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://cosid-proxy");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://cosid-proxy/machines/v2/time?namespace=order")).andRespond(withSuccess("2000", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://cosid-proxy/machines/v2/time")).andRespond(withSuccess("1000", MediaType.APPLICATION_JSON));
        MachineLeaseClient client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build())).build().createClient(MachineLeaseClient.class);
        ProxyMachineLeaseStore store = new ProxyMachineLeaseStore(client);
        
        assertThat(store.serverTime("order"), equalTo(2000L));
        assertThat(store.serverTime(), equalTo(1000L));
        server.verify();
    }
}
