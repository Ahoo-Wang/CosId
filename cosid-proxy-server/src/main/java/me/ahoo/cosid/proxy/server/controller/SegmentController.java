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

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import com.google.common.base.Preconditions;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Segment Controller .
 *
 * @author ahoo wang
 */
@RestController
@RequestMapping("segments")
public class SegmentController {
    private final IdSegmentDistributorFactory distributorFactory;
    private final ConcurrentHashMap<String, IdSegmentDistributor> distributors;
    
    public SegmentController(IdSegmentDistributorFactory distributorFactory) {
        this.distributorFactory = distributorFactory;
        this.distributors = new ConcurrentHashMap<>();
    }
    
    /**
     * Create an ID segment dispatcher, the operation is idempotent.
     */
    @PostMapping("/distributor/{namespace}/{name}")
    public void createDistributor(@PathVariable String namespace, @PathVariable String name, long offset, long step) {
        String namespacedName = IdSegmentDistributor.getNamespacedName(namespace, name);
        distributors.computeIfAbsent(namespacedName,
            key -> distributorFactory.create(new IdSegmentDistributorDefinition(namespace, name, offset, step)));
    }
    
    @PatchMapping("/{namespace}/{name}")
    public long nextMaxId(@PathVariable String namespace, @PathVariable String name, long step) {
        String namespacedName = IdSegmentDistributor.getNamespacedName(namespace, name);
        IdSegmentDistributor distributor = distributors.get(namespacedName);
        Preconditions.checkNotNull(distributor);
        return distributor.nextMaxId(step);
    }
}
