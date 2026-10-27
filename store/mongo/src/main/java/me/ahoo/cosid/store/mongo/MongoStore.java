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

package me.ahoo.cosid.store.mongo;

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;

import com.mongodb.MongoServerException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * The MongoDB store (06-stores.md §2.3): {@link MongoRangeStore} and {@link MongoSlotLeaseStore} on one database,
 * MongoDB 6.0 or later. Every operation reads with {@code majority} concern on the primary, writes with the configured
 * write concern ({@code majority} by default, {@code options.writeConcern}) and runs under the driver's client-side
 * operation timeout ({@code timeoutMS}).
 */
public final class MongoStore implements Store {
    /** The database when {@code options.database} is not set. */
    public static final String DEFAULT_DATABASE = "cosid_db";

    /** The server error code of a duplicate key ({@code E11000}). */
    static final int DUPLICATE_KEY = 11000;

    private final MongoRangeStore ranges;
    private final MongoSlotLeaseStore slots;
    private final MongoClient ownedClient;

    /**
     * A store on a database whose client the caller owns and closes.
     *
     * @param database the database
     * @param timeout  the operation timeout ({@code timeoutMS}); zero means none
     */
    public MongoStore(MongoDatabase database, Duration timeout) {
        this(database, timeout, WriteConcern.MAJORITY);
    }

    /**
     * A store on a database whose client the caller owns and closes, writing with the given concern.
     *
     * @param database     the database
     * @param timeout      the operation timeout ({@code timeoutMS}); zero means none
     * @param writeConcern the write concern, acknowledged; anything weaker than {@code majority} can lose acknowledged
     *                     writes on a failover and so hand out duplicate ids
     */
    public MongoStore(MongoDatabase database, Duration timeout, WriteConcern writeConcern) {
        this(database, timeout, writeConcern, null);
    }

    MongoStore(MongoDatabase database, Duration timeout, WriteConcern writeConcern, MongoClient ownedClient) {
        this.ranges = new MongoRangeStore(database, timeout, writeConcern);
        this.slots = new MongoSlotLeaseStore(database, timeout, writeConcern);
        this.ownedClient = ownedClient;
    }

    @Override
    public RangeStore ranges() {
        return ranges;
    }

    @Override
    public SlotLeaseStore slots() {
        return slots;
    }

    /**
     * Closes the client the store created itself; an injected one stays open.
     */
    @Override
    public void close() {
        if (ownedClient != null) {
            ownedClient.close();
        }
    }

    /**
     * The database as every operation of the store uses it: {@code majority} read and write concern, the primary, and
     * the operation timeout.
     */
    static MongoDatabase configure(MongoDatabase database, Duration timeout) {
        return configure(database, timeout, WriteConcern.MAJORITY);
    }

    /**
     * The database as every operation of the store uses it: the given write concern, {@code majority} read concern, the
     * primary, and the operation timeout.
     */
    static MongoDatabase configure(MongoDatabase database, Duration timeout, WriteConcern writeConcern) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(writeConcern, "writeConcern");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative: " + timeout);
        }
        if (!writeConcern.isAcknowledged()) {
            throw new IllegalArgumentException("the write concern must be acknowledged: " + writeConcern);
        }
        return database.withWriteConcern(writeConcern)
            .withReadConcern(ReadConcern.MAJORITY)
            .withReadPreference(ReadPreference.primary())
            .withTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Whether a write concern is weaker than {@code majority}: any other {@code w}, including the server default.
     */
    static boolean isWeakerThanMajority(WriteConcern writeConcern) {
        Object w = writeConcern.getWObject();
        return !(w instanceof String name && "majority".equalsIgnoreCase(name));
    }

    static boolean isDuplicateKey(MongoServerException e) {
        return e.getCode() == DUPLICATE_KEY;
    }
}
