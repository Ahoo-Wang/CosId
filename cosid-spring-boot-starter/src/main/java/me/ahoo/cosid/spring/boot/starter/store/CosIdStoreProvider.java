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

import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import org.jspecify.annotations.Nullable;

/**
 * A backend for the starter: creates the machine id distributor and/or the id segment distributor factory of one store
 * type.
 *
 * <p>The starter picks the provider whose {@link #getType() type} is the configured store type,
 * {@code cosid.machine.distributor.type} (or {@code cosid.segment.distributor.type}), compared as {@link StoreTypes} do:
 * for example {@code jdbc}, {@code stateful_set} or {@code etcd}. Every built-in backend registers its own provider. A
 * provider bean of the application with the same type as a built-in one takes precedence over it; one with a new type is
 * selected by setting the type to it.</p>
 *
 * <p>A provider supports either side by returning a value from its method, and {@code null} for the side it does not
 * support.</p>
 *
 * <p>The built-in backends check for application providers when they register their beans, by bean type. Define the
 * provider as a bean of its concrete type, in the application's configuration or in an auto-configuration ordered
 * before CosId's backend auto-configurations ({@code @AutoConfiguration(before = ...)}); a provider registered later, or
 * through a raw-typed {@code FactoryBean}, is ignored and a warning is logged at startup.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public interface CosIdStoreProvider {
    
    /**
     * The store type this provider serves, compared ignoring case.
     */
    String getType();
    
    /**
     * Create the machine id distributor.
     *
     * @return the distributor, or {@code null} if this provider has none
     */
    default @Nullable MachineIdDistributor createMachineIdDistributor(MachineStoreContext context) {
        return null;
    }
    
    /**
     * Create the id segment distributor factory.
     *
     * @return the factory, or {@code null} if this provider has none
     */
    default @Nullable IdSegmentDistributorFactory createIdSegmentDistributorFactory(SegmentStoreContext context) {
        return null;
    }
}
