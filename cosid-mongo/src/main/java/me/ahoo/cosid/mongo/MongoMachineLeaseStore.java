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
import static me.ahoo.cosid.mongo.MachineOperates.DISTRIBUTE_TIME_FIELD;
import static me.ahoo.cosid.mongo.MachineOperates.INSTANCE_ID_FIELD;
import static me.ahoo.cosid.mongo.MachineOperates.LAST_TIMESTAMP_FIELD;
import static me.ahoo.cosid.mongo.MachineOperates.MACHINE_ID_FIELD;
import static me.ahoo.cosid.mongo.MachineOperates.NAMESPACE_FIELD;
import static me.ahoo.cosid.mongo.MachineOperates.REVERT_TIME_FIELD;
import static me.ahoo.cosid.mongo.MachineOperates.STABLE_FIELD;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.machine.lease.MachineLeaseStore;

import com.google.common.base.Preconditions;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * {@link MachineLeaseStore} on the {@code cosid_machine} collection: every claim, renewal and release is one atomic
 * {@code findOneAndUpdate} or conditional update.
 *
 * <p>It uses the same documents as {@link MongoMachineIdDistributor} (a released machine id has an empty
 * {@code instanceId}), so instances running either one can share a namespace during an upgrade. The server clock is
 * the {@code localTime} of the {@code hello} command, and every operation has the driver's client-side timeout
 * ({@code timeoutMS}). Claims use pipeline updates, which need MongoDB 4.2 or later.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public class MongoMachineLeaseStore implements MachineLeaseStore {
    /**
     * Upper bound of insert attempts when concurrent instances race for the same new machine id.
     */
    static final int MAX_ATTEMPTS = 32;
    
    private static final FindOneAndUpdateOptions SMALLEST_MACHINE_ID_AFTER = new FindOneAndUpdateOptions()
        .sort(Sorts.ascending(MACHINE_ID_FIELD))
        .returnDocument(ReturnDocument.AFTER);
    
    private final MongoDatabase database;
    private final MongoCollection<Document> collection;
    
    /**
     * Create a MongoDB lease store.
     *
     * @param timeout client-side timeout of each operation, must be positive: the lease adapter holds a lock during
     *                each call
     */
    public MongoMachineLeaseStore(MongoDatabase database, Duration timeout) {
        Preconditions.checkNotNull(database, "database can not be null!");
        Preconditions.checkArgument(timeout != null && !timeout.isNegative() && !timeout.isZero(), "timeout:[%s] must be positive!", timeout);
        this.database = database.withTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        this.collection = this.database.getCollection(MachineCollection.COLLECTION_NAME);
    }
    
    /**
     * Set the last timestamp to {@code max(stored + 1, now)} in the update itself, so the read and the write are atomic.
     */
    private static Bson nextTimestamp(long now) {
        return new Document("$set", new Document(LAST_TIMESTAMP_FIELD,
            new Document("$max", List.of(new Document("$add", List.of("$" + LAST_TIMESTAMP_FIELD, 1L)), now))));
    }
    
    private static Bson take(InstanceId instanceId, long now) {
        return new Document("$set", new Document(INSTANCE_ID_FIELD, instanceId.getInstanceId())
            .append(STABLE_FIELD, instanceId.isStable())
            .append(DISTRIBUTE_TIME_FIELD, now));
    }
    
    private static Optional<Lease> toLease(String namespace, InstanceId instanceId, Document document) {
        if (document == null) {
            return Optional.empty();
        }
        long lastTimestamp = document.getLong(LAST_TIMESTAMP_FIELD);
        return Optional.of(new Lease(namespace, instanceId, document.getInteger(MACHINE_ID_FIELD), lastTimestamp, lastTimestamp));
    }
    
    private Optional<Lease> claim(String namespace, InstanceId instanceId, long now, Bson filter) {
        Document document = collection.findOneAndUpdate(Filters.and(Filters.eq(NAMESPACE_FIELD, namespace), filter),
            List.of(take(instanceId, now), nextTimestamp(now)), SMALLEST_MACHINE_ID_AFTER);
        return toLease(namespace, instanceId, document);
    }
    
    @Override
    public Optional<Lease> claimSelf(String namespace, InstanceId instanceId, long now) {
        return claim(namespace, instanceId, now, Filters.eq(INSTANCE_ID_FIELD, instanceId.getInstanceId()));
    }
    
    @Override
    public Optional<Lease> claimReleased(String namespace, InstanceId instanceId, int maxMachineId, long now) {
        return claim(namespace, instanceId, now, Filters.and(Filters.eq(INSTANCE_ID_FIELD, ""), Filters.lte(MACHINE_ID_FIELD, maxMachineId)));
    }
    
    @Override
    public Optional<Lease> claimExpired(String namespace, InstanceId instanceId, int maxMachineId, long expiredBefore, long now) {
        return claim(namespace, instanceId, now, Filters.and(
            Filters.ne(INSTANCE_ID_FIELD, ""),
            // Documents written before the stable field existed have none, which means not stable.
            Filters.ne(STABLE_FIELD, true),
            Filters.lte(LAST_TIMESTAMP_FIELD, expiredBefore),
            Filters.lte(MACHINE_ID_FIELD, maxMachineId)));
    }
    
    @Override
    public Optional<Lease> claimNew(String namespace, InstanceId instanceId, int maxMachineId, long now) {
        MongoMachineCollection machines = new MongoMachineCollection(collection);
        for (int attempt = 1; ; attempt++) {
            int machineId = machines.nextMachineId(namespace);
            if (machineId > maxMachineId) {
                return Optional.empty();
            }
            try {
                collection.insertOne(MachineOperates.distributeDocument(namespace, instanceId, MachineState.of(machineId, now)));
                return Optional.of(new Lease(namespace, instanceId, machineId, now, now));
            } catch (MongoWriteException mongoWriteException) {
                // Another instance inserted the same machine id first.
                if (mongoWriteException.getError().getCategory() != ErrorCategory.DUPLICATE_KEY || attempt >= MAX_ATTEMPTS) {
                    throw mongoWriteException;
                }
            }
        }
    }
    
    private static Bson current(Lease lease) {
        return Filters.and(
            Filters.eq(Documents.ID_FIELD, namespacedMachineId(lease.getNamespace(), lease.getMachineId())),
            Filters.eq(INSTANCE_ID_FIELD, lease.getInstanceId().getInstanceId()),
            Filters.eq(LAST_TIMESTAMP_FIELD, lease.getVersion()));
    }
    
    @Override
    public Optional<Lease> renew(Lease lease, long now) {
        long timestamp = Math.max(lease.getVersion() + 1, now);
        if (collection.updateOne(current(lease), Updates.set(LAST_TIMESTAMP_FIELD, timestamp)).getMatchedCount() == 0) {
            return Optional.empty();
        }
        return Optional.of(new Lease(lease.getNamespace(), lease.getInstanceId(), lease.getMachineId(), timestamp, timestamp));
    }
    
    @Override
    public boolean release(Lease lease, long lastTimestamp) {
        long timestamp = Math.max(lease.getVersion() + 1, lastTimestamp);
        Bson update = lease.isStable()
            ? Updates.set(LAST_TIMESTAMP_FIELD, timestamp)
            : Updates.combine(
                Updates.set(LAST_TIMESTAMP_FIELD, timestamp),
                Updates.set(INSTANCE_ID_FIELD, ""),
                Updates.set(STABLE_FIELD, false),
                Updates.set(REVERT_TIME_FIELD, timestamp));
        return collection.updateOne(current(lease), update).getMatchedCount() > 0;
    }
    
    /**
     * The {@code localTime} of the server's {@code hello} command.
     */
    @Override
    public long serverTime() {
        Date localTime = database.runCommand(new Document("hello", 1)).getDate("localTime");
        return Preconditions.checkNotNull(localTime, "hello returned no localTime!").getTime();
    }
}
