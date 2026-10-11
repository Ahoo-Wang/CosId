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

package me.ahoo.cosid.spring.boot.starter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;

import org.junit.jupiter.api.Test;

import java.util.List;

class CosIdStoreProvidersTest {
    private static final SegmentStoreContext CONTEXT = new SegmentStoreContext(new SegmentIdProperties());
    
    private static IdSegmentDistributorFactory create(String type, CosIdStoreProvider... providers) {
        return CosIdStoreProviders.create(type, List.of(providers), provider -> provider.createIdSegmentDistributorFactory(CONTEXT), "factory");
    }
    
    private static CosIdStoreProvider user(String type, IdSegmentDistributorFactory factory) {
        return new CosIdStoreProvider() {
            @Override
            public String getType() {
                return type;
            }
            
            @Override
            public IdSegmentDistributorFactory createIdSegmentDistributorFactory(SegmentStoreContext context) {
                return factory;
            }
        };
    }
    
    @Test
    void storeTypeIsTheProviderWhenSetOtherwiseTheLowerCaseType() {
        assertThat(CosIdStoreProviders.storeType(null, MachineProperties.Distributor.Type.STATEFUL_SET)).isEqualTo("stateful_set");
        assertThat(CosIdStoreProviders.storeType(" ", SegmentIdProperties.Distributor.Type.REDIS)).isEqualTo("redis");
        assertThat(CosIdStoreProviders.storeType(" etcd ", SegmentIdProperties.Distributor.Type.REDIS)).isEqualTo("etcd");
    }
    
    @Test
    void applicationProviderTakesPrecedenceOverBuiltIn() {
        IdSegmentDistributorFactory builtIn = mock(IdSegmentDistributorFactory.class);
        IdSegmentDistributorFactory custom = mock(IdSegmentDistributorFactory.class);
        
        IdSegmentDistributorFactory actual = create("REDIS", BuiltInStoreProvider.segment("redis", context -> builtIn), user("redis", custom));
        
        assertThat(actual).isSameAs(custom);
    }
    
    @Test
    void builtInProvidersAreRankedByOrder() {
        IdSegmentDistributorFactory first = mock(IdSegmentDistributorFactory.class);
        IdSegmentDistributorFactory second = mock(IdSegmentDistributorFactory.class);
        
        IdSegmentDistributorFactory actual = create("mongo",
            BuiltInStoreProvider.segment("mongo", 1, context -> second), BuiltInStoreProvider.segment("mongo", 0, context -> first));
        
        assertThat(actual).isSameAs(first);
    }
    
    @Test
    void providerWithoutTheSideIsSkipped() {
        IdSegmentDistributorFactory builtIn = mock(IdSegmentDistributorFactory.class);
        CosIdStoreProvider machineOnly = () -> "redis";
        
        assertThat(create("redis", machineOnly, BuiltInStoreProvider.segment("redis", context -> builtIn))).isSameAs(builtIn);
    }
    
    @Test
    void missingProviderNamesTheTypeAndTheRegisteredOnes() {
        assertThatThrownBy(() -> create("etcd", BuiltInStoreProvider.segment("redis", context -> mock(IdSegmentDistributorFactory.class))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("[etcd]")
            .hasMessageContaining("[redis]");
    }
}
