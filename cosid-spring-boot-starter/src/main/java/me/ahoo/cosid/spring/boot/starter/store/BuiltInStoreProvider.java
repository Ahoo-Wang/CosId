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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;

import java.util.function.Function;

/**
 * A provider registered by one of the starter's own auto-configurations. A provider of the application with the same
 * type takes precedence over it.
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public final class BuiltInStoreProvider implements CosIdStoreProvider, Ordered {
    private final String type;
    private final int order;
    private final @Nullable Function<MachineStoreContext, MachineIdDistributor> machine;
    private final @Nullable Function<SegmentStoreContext, IdSegmentDistributorFactory> segment;
    
    private BuiltInStoreProvider(String type, int order, @Nullable Function<MachineStoreContext, MachineIdDistributor> machine,
                                 @Nullable Function<SegmentStoreContext, IdSegmentDistributorFactory> segment) {
        this.type = Preconditions.checkNotNull(type, "type can not be null!");
        this.order = order;
        this.machine = machine;
        this.segment = segment;
    }
    
    public static BuiltInStoreProvider machine(String type, Function<MachineStoreContext, MachineIdDistributor> machine) {
        return new BuiltInStoreProvider(type, LOWEST_PRECEDENCE, machine, null);
    }
    
    public static BuiltInStoreProvider segment(String type, Function<SegmentStoreContext, IdSegmentDistributorFactory> segment) {
        return segment(type, LOWEST_PRECEDENCE, segment);
    }
    
    /**
     * A segment provider ranked by {@code order} among built-in providers of the same type.
     */
    public static BuiltInStoreProvider segment(String type, int order, Function<SegmentStoreContext, IdSegmentDistributorFactory> segment) {
        return new BuiltInStoreProvider(type, order, null, segment);
    }
    
    @Override
    public String getType() {
        return type;
    }
    
    @Override
    public int getOrder() {
        return order;
    }
    
    @Override
    public @Nullable MachineIdDistributor createMachineIdDistributor(MachineStoreContext context) {
        return machine == null ? null : machine.apply(context);
    }
    
    @Override
    public @Nullable IdSegmentDistributorFactory createIdSegmentDistributorFactory(SegmentStoreContext context) {
        return segment == null ? null : segment.apply(context);
    }
    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("type", type)
            .add("machine", machine != null)
            .add("segment", segment != null)
            .toString();
    }
}
