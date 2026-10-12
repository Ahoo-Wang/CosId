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

import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * The condition of {@link ConditionalOnMachineStoreType} and {@link ConditionalOnSegmentStoreType}.
 */
class OnStoreTypeCondition extends SpringBootCondition {
    
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> machine = metadata.getAnnotationAttributes(ConditionalOnMachineStoreType.class.getName());
        if (machine != null) {
            return outcome(context, MachineProperties.Distributor.TYPE, MachineProperties.Distributor.MANUAL, (String) machine.get("value"));
        }
        Map<String, Object> segment = metadata.getAnnotationAttributes(ConditionalOnSegmentStoreType.class.getName());
        if (segment != null) {
            return outcome(context, SegmentIdProperties.Distributor.TYPE, SegmentIdProperties.Distributor.REDIS, (String) segment.get("value"));
        }
        return ConditionOutcome.noMatch("no store type annotation");
    }
    
    private static ConditionOutcome outcome(ConditionContext context, String key, String defaultType, String expected) {
        String actual = StoreTypes.normalize(context.getEnvironment().getProperty(key), defaultType);
        if (actual.equals(StoreTypes.normalize(expected, expected))) {
            return ConditionOutcome.match(key + " is " + actual);
        }
        return ConditionOutcome.noMatch(key + " is " + actual + ", not " + expected);
    }
}
