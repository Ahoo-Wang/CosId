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
 * MongoDB 4.4 or later. Every operation reads and writes with {@code majority} concern on the primary and runs under the
 * driver's client-side operation timeout ({@code timeoutMS}).
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
        this(database, timeout, null);
    }

    MongoStore(MongoDatabase database, Duration timeout, MongoClient ownedClient) {
        this.ranges = new MongoRangeStore(database, timeout);
        this.slots = new MongoSlotLeaseStore(database, timeout);
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
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative: " + timeout);
        }
        return database.withWriteConcern(WriteConcern.MAJORITY)
            .withReadConcern(ReadConcern.MAJORITY)
            .withReadPreference(ReadPreference.primary())
            .withTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    static boolean isDuplicateKey(MongoServerException e) {
        return e.getCode() == DUPLICATE_KEY;
    }
}
