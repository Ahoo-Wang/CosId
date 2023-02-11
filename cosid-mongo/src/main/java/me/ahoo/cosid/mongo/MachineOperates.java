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

package me.ahoo.cosid.mongo;

import static me.ahoo.cosid.machine.MachineIdDistributor.namespacedMachineId;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineState;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public interface MachineOperates {
    String NAMESPACE_FIELD = "namespace";
    String MACHINE_ID_FIELD = "machineId";
    String LAST_TIMESTAMP_FIELD = "lastTimestamp";
    String INSTANCE_ID_FIELD = "instanceId";
    String DISTRIBUTE_TIME_FIELD = "distributeTime";
    String REVERT_TIME_FIELD = "revertTime";
    String MAX_MACHINE_ID_FIELD = "maxMachineId";
    
    static List<? extends Bson> nextMachineIdPipeline(String namespace) {
        return Arrays.asList(
            Aggregates.match(Filters.eq(NAMESPACE_FIELD, namespace)),
            Aggregates.group("$" + NAMESPACE_FIELD,
                Accumulators.max(MAX_MACHINE_ID_FIELD, "$" + MACHINE_ID_FIELD)
            )
        );
    }
    
    static Document distributeDocument(String namespace, InstanceId instanceId, MachineState nextMachineState) {
        String namespacedMachineId = namespacedMachineId(namespace, nextMachineState.getMachineId());
        return new Document()
            .append(Documents.ID_FIELD, namespacedMachineId)
            .append(NAMESPACE_FIELD, namespace)
            .append(MACHINE_ID_FIELD, nextMachineState.getMachineId())
            .append(LAST_TIMESTAMP_FIELD, nextMachineState.getLastTimeStamp())
            .append(INSTANCE_ID_FIELD, instanceId.getInstanceId())
            .append(DISTRIBUTE_TIME_FIELD, System.currentTimeMillis())
            .append(REVERT_TIME_FIELD, 0L);
    }
    
    static Bson distributeByRevertFilter(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
        return Filters.and(
            Filters.eq(NAMESPACE_FIELD, namespace),
            Filters.or(
                Filters.eq(INSTANCE_ID_FIELD, ""),
                Filters.lte(LAST_TIMESTAMP_FIELD, MachineIdDistributor.getSafeGuardAt(safeGuardDuration, instanceId.isStable()))
            )
        );
    }
    
    static Bson distributeByRevertUpdate(InstanceId instanceId, long lastTimestamp) {
        return Updates.combine(
            Updates.set(INSTANCE_ID_FIELD, instanceId.getInstanceId()),
            Updates.set(LAST_TIMESTAMP_FIELD, lastTimestamp)
        );
    }
    
    static Bson distributeBySelfFilter(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
        return Filters.and(
            Filters.eq(NAMESPACE_FIELD, namespace),
            Filters.eq(INSTANCE_ID_FIELD, instanceId.getInstanceId()),
            Filters.gt(LAST_TIMESTAMP_FIELD, MachineIdDistributor.getSafeGuardAt(safeGuardDuration, instanceId.isStable()))
        );
    }
    
    static Bson distributeBySelfUpdate(long lastTimestamp) {
        return Updates.set(LAST_TIMESTAMP_FIELD, lastTimestamp);
    }
    
    static Bson revertFilter(String namespace, InstanceId instanceId, MachineState machineState) {
        return Filters.and(
            Filters.eq(Documents.ID_FIELD, namespacedMachineId(namespace, machineState.getMachineId())),
            Filters.eq(INSTANCE_ID_FIELD, instanceId.getInstanceId())
        );
    }
    
    static Bson revertUpdate(InstanceId instanceId, MachineState machineState) {
        return Updates.combine(
            Updates.set(INSTANCE_ID_FIELD, instanceId.isStable() ? instanceId.getInstanceId() : ""),
            Updates.set(REVERT_TIME_FIELD, System.currentTimeMillis()),
            Updates.set(LAST_TIMESTAMP_FIELD, machineState.getLastTimeStamp())
        );
    }
    
    
    static Bson guardFilter(String namespace, InstanceId instanceId, MachineState machineState) {
        return Filters.and(
            Filters.eq(Documents.ID_FIELD, namespacedMachineId(namespace, machineState.getMachineId())),
            Filters.eq(INSTANCE_ID_FIELD, instanceId.getInstanceId())
        );
    }
    
    static Bson guardUpdate(long lastTimestamp) {
        return Updates.set(LAST_TIMESTAMP_FIELD, lastTimestamp);
    }
}
