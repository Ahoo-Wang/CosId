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

import static me.ahoo.cosid.mongo.MachineOperates.MACHINE_ID_FIELD;
import static me.ahoo.cosid.mongo.MachineOperates.distributeByRevertFilter;
import static me.ahoo.cosid.mongo.MachineOperates.distributeByRevertUpdate;
import static me.ahoo.cosid.mongo.MachineOperates.distributeBySelfFilter;
import static me.ahoo.cosid.mongo.MachineOperates.distributeBySelfUpdate;
import static me.ahoo.cosid.mongo.MachineOperates.distributeDocument;
import static me.ahoo.cosid.mongo.MachineOperates.guardFilter;
import static me.ahoo.cosid.mongo.MachineOperates.guardUpdate;
import static me.ahoo.cosid.mongo.MachineOperates.nextMachineIdPipeline;
import static me.ahoo.cosid.mongo.MachineOperates.revertFilter;
import static me.ahoo.cosid.mongo.MachineOperates.revertUpdate;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.time.Duration;
import java.util.Objects;

@Slf4j
public class MongoMachineCollection implements MachineCollection {
    private final MongoCollection<Document> machineCollection;
    
    public MongoMachineCollection(MongoCollection<Document> machineCollection) {
        this.machineCollection = machineCollection;
    }
    
    @Override
    public int nextMachineId(String namespace) {
        Document maxMachineIdDoc = machineCollection.aggregate(
            nextMachineIdPipeline(namespace)
        ).first();
        if (maxMachineIdDoc == null) {
            return 0;
        }
        Integer maxMachineId = maxMachineIdDoc.getInteger(MachineOperates.MAX_MACHINE_ID_FIELD);
        return Objects.requireNonNull(maxMachineId) + 1;
    }
    
    @Override
    public MachineState distribute(String namespace, int machineBit, InstanceId instanceId) {
        int nextMachineId = nextMachineId(namespace);
        if (nextMachineId > MachineIdDistributor.maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(MachineIdDistributor.totalMachineIds(machineBit), instanceId);
        }
        MachineState nextMachineState = MachineState.of(nextMachineId, System.currentTimeMillis());
        try {
            machineCollection.insertOne(
                distributeDocument(namespace, instanceId, nextMachineState)
            );
            return nextMachineState;
        } catch (MongoWriteException mongoWriteException) {
            if (mongoWriteException.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                if (log.isInfoEnabled()) {
                    log.info("Distribute Failed:[{}]", mongoWriteException.getMessage());
                }
                return distribute(namespace, machineBit, instanceId);
            }
            throw mongoWriteException;
        }
    }
    
    @Override
    public MachineState distributeByRevert(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
        long lastTimestamp = System.currentTimeMillis();
        Document afterDoc = machineCollection.findOneAndUpdate(
            distributeByRevertFilter(namespace, instanceId, safeGuardDuration),
            distributeByRevertUpdate(instanceId, lastTimestamp),
            Documents.UPDATE_AFTER_OPTIONS
        );
        if (afterDoc == null) {
            return null;
        }
        int machineId = afterDoc.getInteger(MACHINE_ID_FIELD);
        return MachineState.of(machineId, lastTimestamp);
    }
    
    @Override
    public MachineState distributeBySelf(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
        long lastTimestamp = System.currentTimeMillis();
        Document afterDoc = machineCollection.findOneAndUpdate(
            distributeBySelfFilter(namespace, instanceId, safeGuardDuration),
            distributeBySelfUpdate(lastTimestamp),
            Documents.UPDATE_AFTER_OPTIONS
        );
        if (afterDoc == null) {
            return null;
        }
        int machineId = afterDoc.getInteger(MACHINE_ID_FIELD);
        return MachineState.of(machineId, lastTimestamp);
    }
    
    @Override
    public void revert(String namespace, InstanceId instanceId, MachineState machineState) throws MachineIdLostException {
        if (log.isInfoEnabled()) {
            log.info("Revert [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        UpdateResult updateResult = machineCollection.updateOne(
            revertFilter(namespace, instanceId, machineState),
            revertUpdate(instanceId, machineState)
        );
        if (updateResult.getMatchedCount() == 0) {
            throw new MachineIdLostException(namespace, instanceId, machineState);
        }
    }
    
    @Override
    public void guard(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) throws MachineIdLostException {
        if (log.isDebugEnabled()) {
            log.debug("Guard - [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        
        UpdateResult updateResult = machineCollection.updateOne(
            guardFilter(namespace, instanceId, machineState),
            guardUpdate(machineState.getLastTimeStamp())
        );
        if (updateResult.getMatchedCount() == 0) {
            throw new MachineIdLostException(namespace, instanceId, machineState);
        }
    }
}
