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

package me.ahoo.cosid.test.machine.lease;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.machine.lease.MachineLeaseStore;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link MachineLeaseStore} in memory, the reference implementation of {@link MachineLeaseStoreSpec}.
 * Not for production: nothing is shared between processes.
 */
public class InMemoryMachineLeaseStore implements MachineLeaseStore {
    
    private static final class Record {
        @Nullable
        private InstanceId instanceId;
        private long lastTimestamp;
        private long version;
        
        private Record(@Nullable InstanceId instanceId, long lastTimestamp, long version) {
            this.instanceId = instanceId;
            this.lastTimestamp = lastTimestamp;
            this.version = version;
        }
    }
    
    private final Map<String, TreeMap<Integer, Record>> namespaces = new ConcurrentHashMap<>();
    private long nextVersion = 1;
    
    private TreeMap<Integer, Record> records(String namespace) {
        return namespaces.computeIfAbsent(namespace, key -> new TreeMap<>());
    }
    
    private Lease take(String namespace, int machineId, Record record, InstanceId instanceId, long now) {
        record.instanceId = instanceId;
        record.lastTimestamp = Math.max(record.lastTimestamp, now);
        record.version = nextVersion++;
        return new Lease(namespace, instanceId, machineId, record.lastTimestamp, record.version);
    }
    
    @Override
    public synchronized Optional<Lease> claimSelf(String namespace, InstanceId instanceId, long now) {
        for (Map.Entry<Integer, Record> entry : records(namespace).entrySet()) {
            if (instanceId.getInstanceId().equals(instanceIdOf(entry.getValue()))) {
                return Optional.of(take(namespace, entry.getKey(), entry.getValue(), instanceId, now));
            }
        }
        return Optional.empty();
    }
    
    @Override
    public synchronized Optional<Lease> claimReleased(String namespace, InstanceId instanceId, long now) {
        for (Map.Entry<Integer, Record> entry : records(namespace).entrySet()) {
            if (entry.getValue().instanceId == null) {
                return Optional.of(take(namespace, entry.getKey(), entry.getValue(), instanceId, now));
            }
        }
        return Optional.empty();
    }
    
    @Override
    public synchronized Optional<Lease> claimNew(String namespace, InstanceId instanceId, int maxMachineId, long now) {
        TreeMap<Integer, Record> records = records(namespace);
        int machineId = records.isEmpty() ? 0 : records.lastKey() + 1;
        if (machineId > maxMachineId) {
            return Optional.empty();
        }
        Record record = new Record(null, now, 0);
        records.put(machineId, record);
        return Optional.of(take(namespace, machineId, record, instanceId, now));
    }
    
    @Override
    public synchronized Optional<Lease> claimExpired(String namespace, InstanceId instanceId, long expiredBefore, long now) {
        for (Map.Entry<Integer, Record> entry : records(namespace).entrySet()) {
            Record record = entry.getValue();
            if (record.instanceId != null && !record.instanceId.isStable() && record.lastTimestamp <= expiredBefore) {
                return Optional.of(take(namespace, entry.getKey(), record, instanceId, now));
            }
        }
        return Optional.empty();
    }
    
    @Override
    public synchronized Optional<Lease> renew(Lease lease, long now) {
        Record record = current(lease);
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(take(lease.getNamespace(), lease.getMachineId(), record, lease.getInstanceId(), now));
    }
    
    @Override
    public synchronized boolean release(Lease lease, long lastTimestamp) {
        Record record = current(lease);
        if (record == null) {
            return false;
        }
        record.lastTimestamp = Math.max(record.lastTimestamp, lastTimestamp);
        record.version = nextVersion++;
        if (!lease.isStable()) {
            record.instanceId = null;
        }
        return true;
    }
    
    @Nullable
    private Record current(Lease lease) {
        Record record = records(lease.getNamespace()).get(lease.getMachineId());
        if (record == null || record.version != lease.getVersion() || !lease.getInstanceId().getInstanceId().equals(instanceIdOf(record))) {
            return null;
        }
        return record;
    }
    
    @Nullable
    private static String instanceIdOf(Record record) {
        return record.instanceId == null ? null : record.instanceId.getInstanceId();
    }
}
