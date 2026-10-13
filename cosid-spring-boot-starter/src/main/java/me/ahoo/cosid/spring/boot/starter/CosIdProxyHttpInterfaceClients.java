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

package me.ahoo.cosid.spring.boot.starter;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Creates the CosId proxy clients without CoApi ({@code cosid.proxy.client=http-interface}), with
 * {@code cosid.proxy.host}, the timeouts and the token applied directly.
 *
 * @author ahoo wang
 * @since 4.1.0
 */
public final class CosIdProxyHttpInterfaceClients {
    
    static final String LOAD_BALANCED_SCHEME = "lb://";
    
    private CosIdProxyHttpInterfaceClients() {
    }
    
    static boolean isSet(@Nullable Duration timeout) {
        return timeout != null && !timeout.isZero() && !timeout.isNegative();
    }
    
    /**
     * The request factory bounded by the proxy timeouts.
     *
     * @param proxy the proxy properties
     * @return the request factory, or {@code null} when both timeouts are disabled
     */
    @Nullable
    static JdkClientHttpRequestFactory requestFactory(ProxyProperties proxy) {
        if (!isSet(proxy.getConnectTimeout()) && !isSet(proxy.getReadTimeout())) {
            return null;
        }
        // HTTP/1.1 like the other Spring request factories: the JDK client would otherwise try an h2c upgrade.
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1);
        if (isSet(proxy.getConnectTimeout())) {
            httpClientBuilder.connectTimeout(proxy.getConnectTimeout());
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClientBuilder.build());
        if (isSet(proxy.getReadTimeout())) {
            requestFactory.setReadTimeout(proxy.getReadTimeout());
        }
        return requestFactory;
    }
    
    /**
     * The {@code Authorization} header value for the proxy token.
     *
     * @param proxy the proxy properties
     * @return {@code Bearer <token>}, or {@code null} without a token
     */
    @Nullable
    static String authorization(ProxyProperties proxy) {
        String token = proxy.getToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        return "Bearer " + token.trim();
    }
    
    /**
     * A load-balanced {@code lb://} host needs CoApi: the plain clients would fail on their first call.
     *
     * @param host {@code cosid.proxy.host}
     */
    static void checkHost(@Nullable String host) {
        if (host != null && host.trim().regionMatches(true, 0, LOAD_BALANCED_SCHEME, 0, LOAD_BALANCED_SCHEME.length())) {
            throw new IllegalStateException("[cosid.proxy.host:" + host + "] is load-balanced, which the plain proxy clients "
                + "(cosid.proxy.client=http-interface) do not support: set cosid.proxy.client=coapi, with CoApi "
                + "(me.ahoo.coapi:coapi-spring-boot-starter) on the classpath, or a plain http(s) host.");
        }
    }
    
    /**
     * Create a proxy client.
     *
     * @param clientType the client interface, such as {@code MachineLeaseClient}
     * @param proxy      the proxy properties
     * @param builder    the application's {@link RestClient.Builder}, cloned; {@code null} for a new one
     * @param <T>        the client type
     * @return the client
     */
    public static <T> T create(Class<T> clientType, ProxyProperties proxy, RestClient.@Nullable Builder builder) {
        checkHost(proxy.getHost());
        RestClient.Builder restClient = (builder == null ? RestClient.builder() : builder.clone()).baseUrl(proxy.getHost());
        JdkClientHttpRequestFactory requestFactory = requestFactory(proxy);
        if (requestFactory != null) {
            restClient.requestFactory(requestFactory);
        }
        String authorization = authorization(proxy);
        if (authorization != null) {
            restClient.defaultHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient.build())).build().createClient(clientType);
    }
    
    /**
     * Create a proxy client from the beans of the application context.
     *
     * @param clientType        the client interface, such as {@code MachineLeaseClient}
     * @param cosIdProperties   the CosId properties, defaults when missing
     * @param restClientBuilder the application's {@link RestClient.Builder}, if any
     * @param <T>               the client type
     * @return the client
     */
    public static <T> T create(Class<T> clientType, ObjectProvider<CosIdProperties> cosIdProperties,
                               ObjectProvider<RestClient.Builder> restClientBuilder) {
        CosIdProperties properties = cosIdProperties.getIfAvailable();
        ProxyProperties proxy = properties == null ? new ProxyProperties() : properties.getProxy();
        return create(clientType, proxy, restClientBuilder.getIfAvailable());
    }
}
