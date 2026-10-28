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

package me.ahoo.cosid.server;

import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.spring.autoconfigure.CosIdGeneratorRegistrar;
import me.ahoo.cosid.store.StoreProvider;

import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * cosid-server (08-remote.md): the range and slot lease primitives of its backing store at the root (no version prefix, D30), and
 * optionally the generators of its {@code CosIdRuntime}. Stateless; the backing store keeps every invariant, so
 * instances scale out.
 */
@SpringBootApplication
@EnableConfigurationProperties(ServerProperties.class)
public class CosIdServer {
    /**
     * Starts the server.
     *
     * @param args the command line
     */
    public static void main(String[] args) {
        SpringApplication.run(CosIdServer.class, args);
    }

    @Bean
    Grants cosIdServerGrants(ServerProperties properties) {
        return Grants.of(properties.auth());
    }

    /**
     * The bearer authentication of every path. A client token gets only the client paths, wherever
     * {@code spring.mvc.servlet.path}, {@code management.endpoints.web.base-path} or {@code springdoc.*.path} move the
     * rest (R1-server-01); {@code docs.public} adds the springdoc paths to the public ones.
     */
    @Bean
    FilterRegistrationBean<AuthFilter> cosIdServerAuthFilter(Grants grants, ServerProperties properties, Environment environment) {
        requireOneManagementPort(environment, grants);
        String servlet = AuthFilter.servletPrefix(environment.getProperty("spring.mvc.servlet.path"));
        List<String> publicPaths = new ArrayList<>(properties.auth().publicPaths());
        if (properties.docs().publicAccess()) {
            ServerProperties.Docs.paths(environment.getProperty("springdoc.api-docs.path", "/v3/api-docs"),
                environment.getProperty("springdoc.swagger-ui.path", "/swagger-ui.html")).forEach(path -> publicPaths.add(servlet + path));
        }
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>(new AuthFilter(grants, publicPaths, AuthFilter.clientPaths(servlet)));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /**
     * Actuator on its own port runs in a child context this filter does not reach, which would open the {@code cosid}
     * endpoint (it issues ids) to anyone: refuse that unless the server is open anyway (R1-server-01).
     */
    static void requireOneManagementPort(Environment environment, Grants grants) {
        // The rule of Spring Boot's ManagementPortType: a negative port disables Actuator over HTTP, 0 is a random port of its own.
        Integer port = environment.getProperty("management.server.port", Integer.class);
        Integer serverPort = environment.getProperty("server.port", Integer.class);
        if (port == null || port < 0 || grants.anonymous() || serverPort == null && port == 8080 || port != 0 && port.equals(serverPort)) {
            return;
        }
        throw new IllegalStateException("cosid-server refuses management.server.port=" + port + ": Actuator on a port of its own is not "
            + "authenticated, and its cosid endpoint issues ids. Keep Actuator on server.port (health is public there for probes)");
    }

    @Bean
    Rejections cosIdServerRejections(ObjectProvider<MeterRegistry> registry) {
        return new Rejections(registry.getIfAvailable());
    }

    @Bean(destroyMethod = "close")
    BlockingCalls cosIdServerBlockingCalls(ServerProperties properties, Rejections rejections) {
        return new BlockingCalls(properties.blocking(), rejections);
    }

    @Bean
    Requests cosIdServerRequests(ServerProperties properties) {
        return new Requests(properties.limits());
    }

    @Bean
    KeyCap cosIdServerKeyCap(ServerProperties properties, Rejections rejections) {
        return new KeyCap(properties.limits(), rejections);
    }

    @Bean(destroyMethod = "close")
    BackingStore cosIdServerStore(@Qualifier(CosIdGeneratorRegistrar.SPEC_BEAN) ObjectProvider<RuntimeSpec> spec, ServerProperties properties,
                                  ObjectProvider<StoreProvider> providers) {
        RuntimeSpec runtimeSpec = spec.getIfAvailable();
        if (runtimeSpec == null || runtimeSpec.stores().isEmpty()) {
            throw new IllegalStateException("cosid-server needs a backing store: configure cosid.stores.<name> (and keep cosid.enabled=true)");
        }
        return BackingStore.open(runtimeSpec.stores(), properties.store(), providers.orderedStream().toList(), properties.blocking().threads());
    }

    @Bean
    OpenAPI cosIdServerOpenApi() {
        return new OpenAPI()
            .info(new Info().title("CosId Server").description("The range and slot lease API of CosId, and its optional id endpoints")
                .version("5").license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
            .components(new Components().addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
            .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
