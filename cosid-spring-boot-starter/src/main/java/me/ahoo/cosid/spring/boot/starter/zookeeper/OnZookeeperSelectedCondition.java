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

package me.ahoo.cosid.spring.boot.starter.zookeeper;

import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Matches when ZooKeeper is the machine id distributor or the segment distributor, so that the ZooKeeper client only
 * connects when CosId uses it, or when {@code cosid.zookeeper.enabled=true} is set explicitly (for applications that use
 * the CosId Curator client themselves, or a custom store provider that needs it).
 *
 * @author ahoo wang
 * @since 3.4.0
 */
class OnZookeeperSelectedCondition extends AnyNestedCondition {
    
    OnZookeeperSelectedCondition() {
        super(ConfigurationPhase.REGISTER_BEAN);
    }
    
    @ConditionalOnProperty(value = MachineProperties.Distributor.TYPE, havingValue = "zookeeper")
    interface MachineDistributor {
    }
    
    @ConditionalOnProperty(value = SegmentIdProperties.Distributor.TYPE, havingValue = "zookeeper")
    interface SegmentDistributor {
    }
    
    @ConditionalOnProperty(value = ConditionalOnCosIdZookeeperEnabled.ENABLED_KEY, havingValue = "true")
    interface ExplicitlyEnabled {
    }
}
