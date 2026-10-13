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

package me.ahoo.cosid.proxy.server.controller;

import org.jspecify.annotations.NonNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * Sends {@code Cache-Control: no-store} with the ids of {@link IdController}: they are {@code GET} requests, and a
 * cache between the client and the server would hand out the same id twice.
 *
 * @author ahoo wang
 */
@Component
public class NoStoreIdsWebFilter implements WebFilter {
    private final PathPattern ids = new PathPatternParser().parse("/ids/**");
    
    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        if (HttpMethod.GET.equals(exchange.getRequest().getMethod())
            && ids.matches(exchange.getRequest().getPath().pathWithinApplication())) {
            exchange.getResponse().getHeaders().setCacheControl(CacheControl.noStore());
        }
        return chain.filter(exchange);
    }
}
