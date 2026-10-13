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

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * Chooses how the proxy clients are created: {@code cosid.proxy.client}, and {@code http-interface} whenever CoApi is
 * not on the classpath.
 *
 * @author ahoo wang
 * @since 4.1.0
 */
public abstract class ProxyClientCondition extends SpringBootCondition {
    public static final String CLIENT_KEY = "cosid.proxy.client";
    static final String CO_API_CLASS = "me.ahoo.coapi.spring.EnableCoApi";
    
    private final ProxyProperties.Client expected;
    
    ProxyClientCondition(ProxyProperties.Client expected) {
        this.expected = expected;
    }
    
    static ProxyProperties.Client resolve(ConditionContext context) {
        ProxyProperties.Client configured = Binder.get(context.getEnvironment()).bind(CLIENT_KEY, ProxyProperties.Client.class).orElse(null);
        if (!ClassUtils.isPresent(CO_API_CLASS, context.getClassLoader())) {
            if (configured == ProxyProperties.Client.COAPI) {
                throw new IllegalStateException("[" + CLIENT_KEY + "=coapi] but CoApi is not on the classpath: add "
                    + "me.ahoo.coapi:coapi-spring-boot-starter, or set [" + CLIENT_KEY + "=http-interface].");
            }
            return ProxyProperties.Client.HTTP_INTERFACE;
        }
        return configured == null ? ProxyProperties.Client.COAPI : configured;
    }
    
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ProxyProperties.Client client = resolve(context);
        if (client == expected) {
            return ConditionOutcome.match("Proxy clients are created with " + client);
        }
        return ConditionOutcome.noMatch("Proxy clients are created with " + client + ", not " + expected);
    }
    
    /**
     * Through CoApi: the default when CoApi is on the classpath.
     */
    public static final class OnCoApi extends ProxyClientCondition {
        public OnCoApi() {
            super(ProxyProperties.Client.COAPI);
        }
    }
    
    /**
     * As plain beans: {@code cosid.proxy.client=http-interface}, or CoApi is not on the classpath.
     */
    public static final class OnHttpInterface extends ProxyClientCondition {
        public OnHttpInterface() {
            super(ProxyProperties.Client.HTTP_INTERFACE);
        }
    }
}
