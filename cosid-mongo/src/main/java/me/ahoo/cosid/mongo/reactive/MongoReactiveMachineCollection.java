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

package me.ahoo.cosid.mongo.reactive;

import static me.ahoo.cosid.mongo.MachineCollection.namespacedMachineId;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.mongo.Documents;
import me.ahoo.cosid.mongo.MachineCollection;

import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
public class MongoReactiveMachineCollection implements MachineCollection {
    private final MongoCollection<Document> machineCollection;
    
    public MongoReactiveMachineCollection(MongoCollection<Document> machineCollection) {
        this.machineCollection = machineCollection;
    }
    
    @Override
    public int nextMachineId(String namespace) {
        Publisher<Document> maxMachineIdDocPublisher = machineCollection.aggregate(
            Arrays.asList(
                Aggregates.match(Filters.eq(Documents.NAMESPACE_FIELD, namespace)),
                Aggregates.group("$" + Documents.NAMESPACE_FIELD,
                    Accumulators.max(Documents.MAX_MACHINE_ID_FIELD, "$" + Documents.MACHINE_ID_FIELD)
                )
            )
        );
        Document maxMachineIdDoc = BlockingAdapter.block(maxMachineIdDocPublisher);
        if (maxMachineIdDoc == null) {
            return 0;
        }
        Integer maxMachineId = maxMachineIdDoc.getInteger(Documents.MAX_MACHINE_ID_FIELD);
        return Objects.requireNonNull(maxMachineId) + 1;
    }
    
    @Override
    public MachineState distribute(String namespace, int machineBit, InstanceId instanceId) {
        int nextMachineId = nextMachineId(namespace);
        if (nextMachineId > MachineIdDistributor.maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(MachineIdDistributor.totalMachineIds(machineBit), instanceId);
        }
        MachineState nextMachineState = MachineState.of(nextMachineId, System.currentTimeMillis());
        String namespacedMachineId = namespacedMachineId(namespace, nextMachineId);
        try {
            Publisher<InsertOneResult> insertOneResultPublisher = machineCollection.insertOne(
                new Document()
                    .append(Documents.ID_FIELD, namespacedMachineId)
                    .append(Documents.NAMESPACE_FIELD, namespace)
                    .append(Documents.MACHINE_ID_FIELD, nextMachineId)
                    .append(Documents.LAST_TIMESTAMP_FIELD, nextMachineState.getLastTimeStamp())
                    .append(Documents.INSTANCE_ID_FIELD, instanceId.getInstanceId())
                    .append(Documents.DISTRIBUTE_TIME_FIELD, System.currentTimeMillis())
                    .append(Documents.REVERT_TIME_FIELD, 0L)
            );
            BlockingAdapter.block(insertOneResultPublisher);
            return nextMachineState;
        } catch (MongoWriteException mongoWriteException) {
            if (log.isInfoEnabled()) {
                log.info("Distribute [{}]", mongoWriteException.getMessage(), mongoWriteException);
            }
            return distribute(namespace, machineBit, instanceId);
        }
    }
    
    @Override
    public MachineState distributeByRevert(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
        long lastTimestamp = System.currentTimeMillis();
        Publisher<Document> afterDocPublisher = machineCollection.findOneAndUpdate(
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
        Document afterDoc = BlockingAdapter.block(afterDocPublisher);
        if (afterDoc == null) {
            return null;
        }
        int machineId = afterDoc.getInteger(Documents.MACHINE_ID_FIELD);
        return MachineState.of(machineId, lastTimestamp);
    }
    
    @Override
    public MachineState distributeBySelf(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
        long lastTimestamp = System.currentTimeMillis();
        Publisher<Document> afterDocPublisher = machineCollection.findOneAndUpdate(
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
        Document afterDoc = BlockingAdapter.block(afterDocPublisher);
        if (afterDoc == null) {
            return null;
        }
        int machineId = afterDoc.getInteger(Documents.MACHINE_ID_FIELD);
        return MachineState.of(machineId, lastTimestamp);
    }
    
    @Override
    public void revert(String namespace, InstanceId instanceId, MachineState machineState) throws MachineIdLostException {
        if (log.isInfoEnabled()) {
            log.info("Revert [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        Publisher<UpdateResult> updateResultPublisher = machineCollection.updateOne(
            Filters.and(
                Filters.eq(Documents.ID_FIELD, namespacedMachineId(namespace, machineState.getMachineId())),
                Filters.eq(Documents.INSTANCE_ID_FIELD, instanceId.getInstanceId())
            ),
            Updates.combine(
                Updates.set(Documents.INSTANCE_ID_FIELD, instanceId.isStable() ? instanceId.getInstanceId() : ""),
                Updates.set(Documents.REVERT_TIME_FIELD, System.currentTimeMillis()),
                Updates.set(Documents.LAST_TIMESTAMP_FIELD, machineState.getLastTimeStamp())
            )
        );
        UpdateResult updateResult = BlockingAdapter.block(updateResultPublisher);
        if (updateResult.getModifiedCount() == 0) {
            throw new MachineIdLostException(namespace, instanceId, machineState);
        }
    }
    
    @Override
    public void guard(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) throws MachineIdLostException {
        if (log.isDebugEnabled()) {
            log.debug("Guard - [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        
        Publisher<UpdateResult> updateResultPublisher = machineCollection.updateOne(
            Filters.and(
                Filters.eq(Documents.ID_FIELD, namespacedMachineId(namespace, machineState.getMachineId())),
                Filters.eq(Documents.INSTANCE_ID_FIELD, instanceId.getInstanceId())
            ),
            Updates.set(Documents.LAST_TIMESTAMP_FIELD, machineState.getLastTimeStamp())
        );
        UpdateResult updateResult = BlockingAdapter.block(updateResultPublisher);
        if (updateResult.getModifiedCount() == 0) {
            throw new MachineIdLostException(namespace, instanceId, machineState);
        }
    }
}
