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

package me.ahoo.cosid.proxy.server.configuration;

import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfiguration {
    
    @Bean
    public OpenApiCustomizer openApiCustomizer() {
        var version = MoreObjects.firstNonNull(getClass().getPackage().getImplementationVersion(), "2.3.0");
        return openApi -> {
            var info = new Info()
                .title("CosId Proxy Server")
                .description("Universal, flexible, high-performance distributed ID generator.")
                .contact(new Contact().name("Ahoo Wang").url("https://github.com/Ahoo-Wang/CosId"))
                .license(new License().url("https://github.com/Ahoo-Wang/CosId/blob/main/LICENSE").name("Apache 2.0"))
                .version(version);
            openApi.info(info);
        };
    }
}
