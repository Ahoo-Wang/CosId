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

package me.ahoo.cosid.spring.autoconfigure;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.accessor.IdFiller;
import me.ahoo.cosid.codec.IdCodec;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.HostResolver;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.time.TimeSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Starts the {@link CosIdRuntime} (07-runtime-and-spring.md §4.2): the spec from {@code cosid.*} and the
 * {@link RuntimeSpecCustomizer}s ({@link CosIdGeneratorRegistrar}), the {@link StoreProvider} beans, the clients of the
 * context ({@code DataSource}, Lettuce or {@code RedisConnectionFactory}, {@code MongoClient}, {@code CuratorFramework}),
 * the {@link GeneratorListener} beans and a {@link HostResolver} bean if any (Spring Cloud's {@code InetUtils} gives one,
 * the first non-loopback address, as in 4.x). The runtime is fixed once started (D17);
 * the context closes it after the beans that use its generators, and before the clients it uses.
 */
@AutoConfiguration(afterName = "org.springframework.cloud.commons.util.UtilAutoConfiguration")
@ConditionalOnProperty(prefix = CosIdProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CosIdProperties.class)
@ImportRuntimeHints(CosIdRuntimeHints.class)
public class CosIdAutoConfiguration {
    @Bean
    public static CosIdGeneratorRegistrar cosIdGeneratorRegistrar() {
        return new CosIdGeneratorRegistrar();
    }

    /** Binds the {@link CosIdProperties} bean as {@link CosIdSpecs#bind} does: {@code - friendly: {}} is its defaults. */
    @Bean
    @ConfigurationPropertiesBinding
    public static GenericConverter cosIdEmptyItems() {
        return new CosIdSpecs.EmptyItems();
    }

    @Bean(CosIdGeneratorRegistrar.SPEC_BEAN)
    public RuntimeSpec cosIdRuntimeSpec(CosIdGeneratorRegistrar registrar) {
        return registrar.spec();
    }

    @Bean(name = CosIdGeneratorRegistrar.RUNTIME_BEAN, destroyMethod = "close")
    public CosIdRuntime cosIdRuntime(@Qualifier(CosIdGeneratorRegistrar.SPEC_BEAN) RuntimeSpec spec, CosIdProperties properties,
                                     ObjectProvider<StoreProvider> storeProviders, ObjectProvider<GeneratorListener> listeners,
                                     ObjectProvider<HostResolver> hostResolver, ConfigurableListableBeanFactory beanFactory,
                                     Environment environment) {
        RuntimeOptions defaults = RuntimeOptions.defaults();
        RuntimeOptions.Builder options = RuntimeOptions.builder()
            .time(TimeSource.SYSTEM)
            .listener(CompositeListener.of(listeners.orderedStream().toList()))
            .hostResolver(hostResolver.getIfAvailable(() -> HostResolver.LOCAL))
            .codecs(reference -> beanFactory.containsBean(reference) && beanFactory.isTypeMatch(reference, IdCodec.class)
                ? Optional.of(beanFactory.getBean(reference, IdCodec.class)) : defaults.codecs().apply(reference))
            // strategy: custom refers to a bean by name (D41); the bean must not depend on the runtime.
            .generators(reference -> beanFactory.containsBean(reference) && beanFactory.isTypeMatch(reference, IdGenerator.class)
                ? Optional.of(beanFactory.getBean(reference, IdGenerator.class)) : Optional.empty())
            .countGenerated(properties.metrics().generated().enabled());
        storeProviders.orderedStream().forEach(options::storeProvider);
        CosIdStoreClients.resolve(spec, properties, beanFactory, CosIdGeneratorRegistrar.RUNTIME_BEAN).forEach(options::client);
        port(environment).ifPresent(options::port);
        return CosIdRuntime.start(spec, options.build());
    }

    @Bean
    public IdFiller cosIdFiller(CosIdRuntime runtime) {
        return runtime.filler();
    }

    /** The owner key host from Spring Cloud's {@code InetUtils} (07 §1.1). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.cloud.commons.util.InetUtils")
    static class CloudHost {
        @Bean
        @ConditionalOnBean(InetUtils.class)
        @ConditionalOnMissingBean
        HostResolver cosIdHostResolver(InetUtils inetUtils) {
            return () -> inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
        }
    }

    /** {@code server.port} for the owner key {@code host:port} (07 §1.1); none when unset or random. */
    static OptionalInt port(Environment environment) {
        Integer port = environment.getProperty("server.port", Integer.class);
        return port == null || port <= 0 ? OptionalInt.empty() : OptionalInt.of(port);
    }

    /** Every listener bean, each isolated from the others' failures. */
    static final class CompositeListener implements GeneratorListener {
        private static final Logger log = LoggerFactory.getLogger(CompositeListener.class);
        private final List<GeneratorListener> listeners;

        private CompositeListener(List<GeneratorListener> listeners) {
            this.listeners = listeners;
        }

        static GeneratorListener of(List<GeneratorListener> listeners) {
            return switch (listeners.size()) {
                case 0 -> GeneratorListener.NOOP;
                case 1 -> listeners.getFirst();
                default -> new CompositeListener(List.copyOf(listeners));
            };
        }

        private void each(Consumer<GeneratorListener> event) {
            for (GeneratorListener listener : listeners) {
                try {
                    event.accept(listener);
                } catch (RuntimeException e) {
                    log.warn("GeneratorListener {} failed", listener, e);
                }
            }
        }

        @Override
        public void onRangeReserved(String generator, String key, Range range, Duration latency) {
            each(listener -> listener.onRangeReserved(generator, key, range, latency));
        }

        @Override
        public void onRefillFailed(String generator, String key, Throwable error) {
            each(listener -> listener.onRefillFailed(generator, key, error));
        }

        @Override
        public void onBatchResized(String generator, long oldSize, long newSize) {
            each(listener -> listener.onBatchResized(generator, oldSize, newSize));
        }

        @Override
        public void onSequenceExhausted(String generator) {
            each(listener -> listener.onSequenceExhausted(generator));
        }

        @Override
        public void onClockRegression(String generator, long backwardsMillis, String action) {
            each(listener -> listener.onClockRegression(generator, backwardsMillis, action));
        }

        @Override
        public void onWindowExceeded(String generator) {
            each(listener -> listener.onWindowExceeded(generator));
        }

        @Override
        public void onSlotAcquired(SlotLease lease, String reason) {
            each(listener -> listener.onSlotAcquired(lease, reason));
        }

        @Override
        public void onSlotRenewed(SlotLease lease) {
            each(listener -> listener.onSlotRenewed(lease));
        }

        @Override
        public void onSlotRenewFailed(SlotLease lease, Throwable error) {
            each(listener -> listener.onSlotRenewFailed(lease, error));
        }

        @Override
        public void onSlotLost(SlotLease lease) {
            each(listener -> listener.onSlotLost(lease));
        }

        @Override
        public void onSlotReleased(SlotLease lease) {
            each(listener -> listener.onSlotReleased(lease));
        }

        @Override
        public void onClockSkew(String namespace, long skewMillis) {
            each(listener -> listener.onClockSkew(namespace, skewMillis));
        }

        @Override
        public void onDuplicateOwnerKey(String namespace, String ownerKey) {
            each(listener -> listener.onDuplicateOwnerKey(namespace, ownerKey));
        }

        @Override
        public void onStoreTimeout(String store, String operation) {
            each(listener -> listener.onStoreTimeout(store, operation));
        }
    }
}
