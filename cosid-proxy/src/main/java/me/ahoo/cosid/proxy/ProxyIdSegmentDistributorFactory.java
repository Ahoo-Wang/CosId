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

import static me.ahoo.cosid.proxy.ProxyMachineIdDistributor.JSON;

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import com.google.common.base.Strings;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * ProxyIdSegmentDistributorFactory .
 *
 * @author ahoo wang
 */
@Slf4j
public class ProxyIdSegmentDistributorFactory implements IdSegmentDistributorFactory {
    
    private final OkHttpClient client;
    
    private final String proxyHost;
    
    public ProxyIdSegmentDistributorFactory(OkHttpClient client, String proxyHost) {
        this.client = client;
        this.proxyHost = proxyHost;
    }
    
    @SneakyThrows
    @Override
    public IdSegmentDistributor create(IdSegmentDistributorDefinition definition) {
        String apiUrl =
            Strings.lenientFormat("%s/segments/distributor/%s/%s?offset=%s&step=%s", proxyHost, definition.getNamespace(), definition.getName(), definition.getOffset(), definition.getStep());
    
        if (log.isInfoEnabled()) {
            log.info("create - [{}] - apiUrl:[{}].", definition.getNamespacedName(), apiUrl);
        }

        Request request = new Request.Builder()
            .url(apiUrl)
            .post(RequestBody.create(JSON, ""))
            .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            assert responseBody != null;
            String bodyStr = responseBody.string();
            if (log.isInfoEnabled()) {
                log.info("create - [{}] - response:[{}].", definition.getNamespacedName(), bodyStr);
            }
            if (!response.isSuccessful()) {
                throw new IllegalStateException(Strings.lenientFormat("Create Distributor:[%s] - response:[%s].", definition.getNamespacedName(), responseBody.string()));
            }
        }
        
        return new ProxyIdSegmentDistributor(client, proxyHost, definition.getNamespace(), definition.getName(), definition.getStep());
    }
}
