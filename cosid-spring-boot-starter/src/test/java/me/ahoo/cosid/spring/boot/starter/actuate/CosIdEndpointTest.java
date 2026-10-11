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

package me.ahoo.cosid.spring.boot.starter.actuate;

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.StringSegmentId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class CosIdEndpointTest {

    @Test
    void statReportsEveryRegisteredGeneratorByName() {
        DefaultIdGeneratorProvider provider = newProviderWithShareAndOrders();
        CosIdEndpoint endpoint = new CosIdEndpoint(provider);

        assertThat(endpoint.stat())
            .containsOnlyKeys(IdGeneratorProvider.SHARE, "orders")
            .allSatisfy((name, stat) -> assertThat(stat).isNotNull());
    }

    @Test
    void getStatReadsRequiredGenerator() {
        CosIdEndpoint endpoint = new CosIdEndpoint(newProviderWithShareAndOrders());

        assertThat(endpoint.getStat("orders")).isNotNull();
    }

    @Test
    void getStatPropagatesMissingGeneratorFailure() {
        CosIdEndpoint endpoint = new CosIdEndpoint(new DefaultIdGeneratorProvider());

        assertThatThrownBy(() -> endpoint.getStat("missing"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void removeReturnsRemovedGeneratorStatAndUnregistersIt() {
        DefaultIdGeneratorProvider provider = newProviderWithShareAndOrders();
        CosIdEndpoint endpoint = new CosIdEndpoint(provider);

        assertThat(endpoint.remove("orders")).isNotNull();
        assertThat(provider.get("orders")).isEmpty();
        assertThat(provider.getShare()).isSameAs(MockIdGenerator.INSTANCE);
    }

    @Test
    void removeClosesDecoratedSegmentChainId() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        PrefetchWorkerExecutorService executor = new PrefetchWorkerExecutorService(Duration.ofDays(1), 1, false);
        SegmentChainId segmentChainId = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, new AtomicIdSegmentDistributor(), executor);
        provider.set("orders", new StringSegmentId(segmentChainId, Radix62IdConverter.INSTANCE));
        CosIdEndpoint endpoint = new CosIdEndpoint(provider);

        try {
            endpoint.remove("orders");

            assertThat(segmentChainId.isClosed()).isTrue();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void removePropagatesMissingGeneratorFailure() {
        CosIdEndpoint endpoint = new CosIdEndpoint(new DefaultIdGeneratorProvider());

        assertThatThrownBy(() -> endpoint.remove("missing"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("missing");
    }

    private static DefaultIdGeneratorProvider newProviderWithShareAndOrders() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        provider.setShare(MockIdGenerator.INSTANCE);
        provider.set("orders", MockIdGenerator.INSTANCE);
        return provider;
    }
}
