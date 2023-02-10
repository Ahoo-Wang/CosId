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

import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;

import com.google.common.base.Strings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
public class MongoMachineIdDistributor extends AbstractMachineIdDistributor {
    private final MongoCollection<Document> machineCollection;
    
    public MongoMachineIdDistributor(MongoCollection<Document> machineCollection,
                                     MachineStateStorage machineStateStorage,
                                     ClockBackwardsSynchronizer clockBackwardsSynchronizer
    ) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.machineCollection = machineCollection;
    }
    
    static String getNamespacedMachineId(String namespace, int machineId) {
        return namespace + "." + Strings.padStart(String.valueOf(machineId), 8, '0');
    }
    
    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        MachineState machineState = distributeBySelf(namespace, instanceId, safeGuardDuration);
        if (machineState != null) {
            return machineState;
        }
        
        machineState = distributeByRevert(namespace, instanceId, safeGuardDuration);
        if (machineState != null) {
            return machineState;
        }
        return distributeMachine(namespace, machineBit, instanceId, safeGuardDuration);
    }
    
    private MachineState distributeBySelf(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
        long lastTimestamp = System.currentTimeMillis();
        Document afterDoc = machineCollection.findOneAndUpdate(
            Filters.and(
                Filters.eq(Documents.NAMESPACE_FIELD, namespace),
                Filters.eq(Documents.INSTANCE_ID_FIELD, instanceId.getInstanceId()),
                Filters.gt(Documents.LAST_TIMESTAMP_FIELD, MachineIdDistributor.getSafeGuardAt(safeGuardDuration, instanceId.isStable()))
            ),
            Updates.combine(
                Updates.set(Documents.LAST_TIMESTAMP_FIELD, lastTimestamp)
            ),
            Documents.UPDATE_AFTER_OPTIONS
        );
        if (afterDoc == null) {
            return null;
        }
        int machineId = afterDoc.getInteger(Documents.MACHINE_ID_FIELD);
        return MachineState.of(machineId, lastTimestamp);
    }
    
    private MachineState distributeByRevert(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
        long lastTimestamp = System.currentTimeMillis();
        Document afterDoc = machineCollection.findOneAndUpdate(
            Filters.and(
                Filters.eq(Documents.NAMESPACE_FIELD, namespace),
                Filters.or(
                    Filters.eq(Documents.INSTANCE_ID_FIELD, ""),
                    Filters.lte(Documents.LAST_TIMESTAMP_FIELD, MachineIdDistributor.getSafeGuardAt(safeGuardDuration, instanceId.isStable()))
                )
            ),
            Updates.combine(
                Updates.set(Documents.LAST_TIMESTAMP_FIELD, lastTimestamp)
            ),
            Documents.UPDATE_AFTER_OPTIONS
        );
        if (afterDoc == null) {
            return null;
        }
        int machineId = afterDoc.getInteger(Documents.MACHINE_ID_FIELD);
        return MachineState.of(machineId, lastTimestamp);
    }
    
    private MachineState distributeMachine(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        int nextMachineId = nextMachineId(namespace);
        if (nextMachineId > MachineIdDistributor.maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(MachineIdDistributor.totalMachineIds(machineBit), instanceId);
        }
        MachineState nextMachineState = MachineState.of(nextMachineId, System.currentTimeMillis());
        String namespacedMachineId = getNamespacedMachineId(namespace, nextMachineId);
        try {
            machineCollection.insertOne(
                new Document()
                    .append(Documents.ID_FIELD, namespacedMachineId)
                    .append(Documents.NAMESPACE_FIELD, namespace)
                    .append(Documents.MACHINE_ID_FIELD, nextMachineId)
                    .append(Documents.LAST_TIMESTAMP_FIELD, nextMachineState.getLastTimeStamp())
                    .append(Documents.INSTANCE_ID_FIELD, instanceId.getInstanceId())
                    .append(Documents.DISTRIBUTE_TIME_FIELD, System.currentTimeMillis())
                    .append(Documents.REVERT_TIME_FIELD, 0L)
            );
            return nextMachineState;
        } catch (MongoWriteException mongoWriteException) {
            if (log.isInfoEnabled()) {
                log.info("Distribute Machine [{}]", mongoWriteException.getMessage(), mongoWriteException);
            }
            return distributeMachine(namespace, machineBit, instanceId, safeGuardDuration);
        }
    }
    
    int nextMachineId(String namespace) {
        String maxMachineIdField = "maxMachineId";
        Document maxMachineIdDoc = machineCollection.aggregate(
            Arrays.asList(
                Aggregates.match(Filters.eq(Documents.NAMESPACE_FIELD, namespace)),
                Aggregates.group("$" + Documents.NAMESPACE_FIELD,
                    Accumulators.max(maxMachineIdField, "$" + Documents.MACHINE_ID_FIELD)
                )
            )
        ).first();
        if (maxMachineIdDoc == null) {
            return 0;
        }
        Integer maxMachineId = maxMachineIdDoc.getInteger(maxMachineIdField);
        return Objects.requireNonNull(maxMachineId) + 1;
    }
    
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("Revert Remote [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        UpdateResult updateResult = machineCollection.updateOne(
            Filters.and(
                Filters.eq(Documents.ID_FIELD, getNamespacedMachineId(namespace, machineState.getMachineId())),
                Filters.eq(Documents.INSTANCE_ID_FIELD, instanceId.getInstanceId())
            ),
            Updates.combine(
                Updates.set(Documents.INSTANCE_ID_FIELD, instanceId.isStable() ? instanceId.getInstanceId() : ""),
                Updates.set(Documents.REVERT_TIME_FIELD, System.currentTimeMillis()),
                Updates.set(Documents.LAST_TIMESTAMP_FIELD, machineState.getLastTimeStamp())
            )
        );
        if (updateResult.getModifiedCount() == 0) {
            throw new MachineIdLostException(namespace, instanceId, machineState);
        }
    }
    
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        if (log.isDebugEnabled()) {
            log.debug("Guard Remote - [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        
        UpdateResult updateResult = machineCollection.updateOne(
            Filters.and(
                Filters.eq(Documents.ID_FIELD, getNamespacedMachineId(namespace, machineState.getMachineId())),
                Filters.eq(Documents.INSTANCE_ID_FIELD, instanceId.getInstanceId())
            ),
            Updates.set(Documents.LAST_TIMESTAMP_FIELD, machineState.getLastTimeStamp())
        );
        if (updateResult.getModifiedCount() == 0) {
            throw new MachineIdLostException(namespace, instanceId, machineState);
        }
    }
}
