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

import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.proxy.api.SegmentApi;
import me.ahoo.cosid.proxy.server.configuration.ProxyServerProperties;
import me.ahoo.cosid.proxy.server.error.TooManyDistributorsException;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Segment Controller .
 *
 * @author ahoo wang
 */
@RestController
public class SegmentController implements SegmentApi {
    /**
     * Leaves half of the {@code long} range for the segments, so that the offset cannot make the id overflow.
     */
    static final long MAX_OFFSET = Long.MAX_VALUE / 2;
    private final IdSegmentDistributorFactory distributorFactory;
    private final ConcurrentHashMap<String, IdSegmentDistributor> distributors;
    private final long maxStep;
    private final int maxDistributors;

    public SegmentController(IdSegmentDistributorFactory distributorFactory) {
        this(distributorFactory, new ProxyServerProperties());
    }

    @Autowired
    public SegmentController(IdSegmentDistributorFactory distributorFactory, ProxyServerProperties proxyServerProperties) {
        this.distributorFactory = distributorFactory;
        this.distributors = new ConcurrentHashMap<>();
        this.maxStep = proxyServerProperties.getSegment().getMaxStep();
        this.maxDistributors = proxyServerProperties.getSegment().getMaxDistributors();
    }

    private void checkArguments(String namespace, String name, long step) {
        Checks.checkArgument(!Checks.isNullOrEmpty(namespace) && !namespace.isBlank(), "namespace can not be blank!");
        Checks.checkArgument(!Checks.isNullOrEmpty(name) && !name.isBlank(), "name can not be blank!");
        Checks.checkArgument(step > 0, "step:[%s] must be greater than 0!", step);
        Checks.checkArgument(maxStep <= 0 || step <= maxStep, "step:[%s] must not exceed [%s]!", step, maxStep);
    }

    /**
     * Create an ID segment dispatcher, the operation is idempotent.
     */
    @Override
    @Operation(summary = "Create an ID segment dispatcher, the operation is idempotent.")
    public void createDistributor(@PathVariable String namespace, @PathVariable String name, long offset, long step) {
        checkArguments(namespace, name, step);
        Checks.checkArgument(offset >= 0, "offset:[%s] must be greater than or equal to 0!", offset);
        Checks.checkArgument(offset <= MAX_OFFSET, "offset:[%s] must not exceed [%s]!", offset, MAX_OFFSET);
        try {
            Math.addExact(offset, step);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(Checks.lenientFormat("offset:[%s] + step:[%s] overflows!", offset, step), overflow);
        }
        String namespacedName = IdSegmentDistributor.getNamespacedName(namespace, name);
        distributor(namespacedName, () -> new IdSegmentDistributorDefinition(namespace, name, offset, step));
    }

    /**
     * Get next max id.
     *
     * <p>Distributors are cached in memory only. On a cache miss (e.g. after a server restart,
     * when running clients no longer re-send {@code createDistributor}) the distributor is
     * rebuilt lazily with {@link IdSegmentDistributor#DEFAULT_OFFSET}: the backing store
     * applies an offset only on first-ever initialization, so already-allocated segment
     * state survives the rebuild.
     */
    @Override
    @Operation(summary = "Get next max id.")
    public long nextMaxId(@PathVariable String namespace, @PathVariable String name, long step) {
        checkArguments(namespace, name, step);
        String namespacedName = IdSegmentDistributor.getNamespacedName(namespace, name);
        IdSegmentDistributor distributor = distributor(namespacedName,
            () -> new IdSegmentDistributorDefinition(namespace, name, IdSegmentDistributor.DEFAULT_OFFSET, step));
        return distributor.nextMaxId(step);
    }
    
    private IdSegmentDistributor distributor(String namespacedName, Supplier<IdSegmentDistributorDefinition> definition) {
        IdSegmentDistributor distributor = distributors.get(namespacedName);
        if (distributor != null) {
            return distributor;
        }
        // Checked before adding, so concurrent first requests may go slightly over the limit.
        if (maxDistributors > 0 && distributors.size() >= maxDistributors) {
            throw new TooManyDistributorsException(maxDistributors, namespacedName);
        }
        return distributors.computeIfAbsent(namespacedName, key -> distributorFactory.create(definition.get()));
    }
}
