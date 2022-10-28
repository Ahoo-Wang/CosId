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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.annotation.Nonnull;

/**
 * ProxyIdSegmentDistributor .
 *
 * <p><img src="doc-files/CosId-Proxy.png" alt="CosId-Proxy"></p>
 *
 * @author ahoo wang
 */
@Slf4j
public class ProxyIdSegmentDistributor implements IdSegmentDistributor {
    private final OkHttpClient client;
    private final String proxyHost;
    private final String namespace;
    private final String name;
    private final long step;
    
    public ProxyIdSegmentDistributor(OkHttpClient client, String proxyHost, String namespace, String name, long step) {
        this.client = client;
        this.proxyHost = proxyHost;
        this.namespace = namespace;
        this.name = name;
        this.step = step;
    }
    
    @Nonnull
    @Override
    public String getNamespace() {
        return namespace;
    }
    
    @Nonnull
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public long getStep() {
        return step;
    }
    
    @SneakyThrows
    @Override
    public long nextMaxId(long step) {
        String apiUrl =
            Strings.lenientFormat("%s/segments/%s/%s?step=%s", proxyHost, getNamespace(), getName(), step);
        
        Request request = new Request.Builder()
            .url(apiUrl)
            .patch(RequestBody.create(JSON, ""))
            .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            assert responseBody != null;
            String bodyStr = responseBody.string();
            if (log.isInfoEnabled()) {
                log.info("Next Max Id -[{}]- step:[{}] - response:[{}].", getNamespacedName(), step, bodyStr);
            }
            if (!response.isSuccessful()) {
                throw new IllegalStateException(Strings.lenientFormat("Distributor:[%s] - response:[%s]", getNamespacedName(), bodyStr));
            }
            Preconditions.checkNotNull(bodyStr);
            return Long.parseLong(bodyStr);
        }
    }
}
