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

import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;

import com.mongodb.MongoServerException;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * {@link RangeStore} on the {@code cosid_range} collection, {@code {_id: key, next, sealed}} (06-stores.md §2.3).
 *
 * <p>A reservation is one {@code findOneAndUpdate}: an upsert with the pipeline update
 * {@code next = ifNull(next, initialOffset) + size}, filtered on {@code sealed != true} and on {@code next} leaving room
 * for {@code size}, returning the updated document; the range is {@code [next - size, next)}. When the filter matches
 * nothing on an existing key the upsert fails with {@code E11000}, as it does when a concurrent first reservation created
 * the key: the store then reads the key, reports a sealed or exhausted one, and otherwise retries.
 */
public final class MongoRangeStore implements RangeStore {
    /** The collection. */
    public static final String COLLECTION = "cosid_range";
    static final String ID = "_id";
    static final String NEXT = "next";
    static final String SEALED = "sealed";
    /** A first reservation that lost the upsert race retries once; a third attempt covers a stale read in between. */
    static final int ATTEMPTS = 3;
    private static final FindOneAndUpdateOptions UPSERT = new FindOneAndUpdateOptions()
        .upsert(true).returnDocument(ReturnDocument.AFTER).projection(Projections.include(NEXT));
    private static final FindOneAndUpdateOptions UPDATE = new FindOneAndUpdateOptions()
        .returnDocument(ReturnDocument.AFTER).projection(Projections.include(NEXT));

    private final MongoCollection<Document> ranges;

    /**
     * A range store on a database whose client the caller owns.
     *
     * @param database the database
     * @param timeout  the operation timeout ({@code timeoutMS}); zero means none
     */
    public MongoRangeStore(MongoDatabase database, Duration timeout) {
        this(database, timeout, WriteConcern.MAJORITY);
    }

    /**
     * A range store on a database whose client the caller owns, writing with the given concern.
     *
     * @param database     the database
     * @param timeout      the operation timeout ({@code timeoutMS}); zero means none
     * @param writeConcern the write concern, acknowledged ({@code majority} keeps ranges across a failover)
     */
    public MongoRangeStore(MongoDatabase database, Duration timeout, WriteConcern writeConcern) {
        this(MongoStore.configure(database, timeout, writeConcern).getCollection(COLLECTION));
    }

    /** On a given collection, already configured: tests wrap it to inject server errors. */
    MongoRangeStore(MongoCollection<Document> ranges) {
        this.ranges = ranges;
    }

    /** The collection as the store uses it (tests check its concerns). */
    MongoCollection<Document> collection() {
        return ranges;
    }

    @Override
    public Range reserve(String key, long size, long initialOffset) {
        return MongoStore.timed("reserve", () -> reserveNow(key, size, initialOffset));
    }

    private Range reserveNow(String key, long size, long initialOffset) {
        Objects.requireNonNull(key, "key");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        if (initialOffset < 0) {
            throw new IllegalArgumentException("initialOffset must not be negative: " + initialOffset);
        }
        long highestStart = Long.MAX_VALUE - size;
        // A new key whose first range would pass Long.MAX_VALUE is never created: no upsert then.
        boolean create = initialOffset <= highestStart;
        Bson filter = Filters.and(Filters.eq(ID, key), Filters.ne(SEALED, true), Filters.lte(NEXT, highestStart));
        List<Bson> update = List.of(new Document("$set", new Document(NEXT,
            new Document("$add", List.of(new Document("$ifNull", List.of("$" + NEXT, initialOffset)), size)))));
        MongoServerException lastRace = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                Document reserved = ranges.findOneAndUpdate(filter, update, create ? UPSERT : UPDATE);
                if (reserved != null) {
                    long next = reserved.getLong(NEXT);
                    return new Range(next - size, next);
                }
            } catch (MongoServerException e) {
                if (!MongoStore.isDuplicateKey(e)) {
                    throw e;
                }
                lastRace = e;
            }
            // The filter matched no document: the key is sealed, has no room left, or was just created by another reserve.
            Document current = ranges.find(Filters.eq(ID, key)).first();
            if (current == null) {
                if (!create) {
                    throw new RangeExhaustedException(key);
                }
                continue;
            }
            if (Boolean.TRUE.equals(current.getBoolean(SEALED))) {
                throw new RangeSealedException(key);
            }
            Long next = current.getLong(NEXT);
            if (next != null && next > highestStart) {
                throw new RangeExhaustedException(key);
            }
        }
        throw Objects.requireNonNull(lastRace, "a reserve without a race always returns");
    }

    /**
     * Seals the key with an upsert, so a key that does not exist yet is sealed too and never created (S6).
     *
     * @param key the range key
     * @return true
     */
    @Override
    public boolean seal(String key) {
        return MongoStore.timed("seal", () -> sealNow(key));
    }

    private boolean sealNow(String key) {
        Objects.requireNonNull(key, "key");
        for (int attempt = 1; ; attempt++) {
            try {
                ranges.updateOne(Filters.eq(ID, key), Updates.set(SEALED, true), new UpdateOptions().upsert(true));
                return true;
            } catch (MongoServerException e) {
                // A concurrent upsert of the key: the next attempt updates the document it created.
                if (!MongoStore.isDuplicateKey(e) || attempt >= ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}
