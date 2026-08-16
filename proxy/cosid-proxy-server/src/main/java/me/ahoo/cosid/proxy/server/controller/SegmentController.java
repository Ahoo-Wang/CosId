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

import me.ahoo.cosid.proxy.api.SegmentApi;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Segment Controller .
 *
 * @author ahoo wang
 */
@RestController
public class SegmentController implements SegmentApi {
    private final IdSegmentDistributorFactory distributorFactory;
    private final ConcurrentHashMap<String, IdSegmentDistributor> distributors;

    public SegmentController(IdSegmentDistributorFactory distributorFactory) {
        this.distributorFactory = distributorFactory;
        this.distributors = new ConcurrentHashMap<>();
    }

    /**
     * Create an ID segment dispatcher, the operation is idempotent.
     */
    @Override
    @Operation(summary = "Create an ID segment dispatcher, the operation is idempotent.")
    public void createDistributor(@PathVariable String namespace, @PathVariable String name, long offset, long step) {
        String namespacedName = IdSegmentDistributor.getNamespacedName(namespace, name);
        distributors.computeIfAbsent(namespacedName,
            key -> distributorFactory.create(new IdSegmentDistributorDefinition(namespace, name, offset, step)));
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
        String namespacedName = IdSegmentDistributor.getNamespacedName(namespace, name);
        IdSegmentDistributor distributor = distributors.computeIfAbsent(namespacedName,
            key -> distributorFactory.create(new IdSegmentDistributorDefinition(namespace, name, IdSegmentDistributor.DEFAULT_OFFSET, step)));
        return distributor.nextMaxId(step);
    }
}
