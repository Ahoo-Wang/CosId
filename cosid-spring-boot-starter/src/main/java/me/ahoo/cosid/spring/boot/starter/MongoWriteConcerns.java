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

package me.ahoo.cosid.spring.boot.starter;

import com.mongodb.WriteConcern;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The {@code cosid.*.mongo.write-concern} of CosId's MongoDB writes, and the startup check that, on a replica set, it is
 * not weaker than {@code majority}: a write acknowledged by fewer members can be rolled back by a failover, losing a
 * machine id lease or a segment increment that was already handed out.
 *
 * @author ahoo wang
 * @since 4.1.0
 */
@Slf4j
public final class MongoWriteConcerns {
    private MongoWriteConcerns() {
    }
    
    /**
     * Parse a write concern: a name of {@link WriteConcern#valueOf(String)} ({@code majority}, {@code w1},
     * {@code acknowledged}, {@code journaled}, ...) or a number of members.
     *
     * @param property the property, for the error message
     * @throws IllegalArgumentException when it is neither
     */
    public static WriteConcern parse(String writeConcern, String property) {
        String value = writeConcern.trim();
        if (!value.isEmpty() && value.chars().allMatch(Character::isDigit)) {
            return new WriteConcern(Integer.parseInt(value));
        }
        WriteConcern named = WriteConcern.valueOf(value.toUpperCase(Locale.ROOT));
        if (named == null) {
            throw new IllegalArgumentException(property + " [" + writeConcern + "] is not a write concern: use majority, w1, w2, w3, acknowledged, "
                + "journaled or a number of members.");
        }
        return named;
    }
    
    /**
     * The database with the configured write concern, or as it is (the connection string's) when none is configured.
     */
    public static MongoDatabase apply(MongoDatabase database, @Nullable String writeConcern, String property) {
        if (writeConcern == null || writeConcern.isBlank()) {
            return database;
        }
        return database.withWriteConcern(parse(writeConcern, property));
    }
    
    /**
     * WARN when the database is a replica set member and its effective write concern is weaker than {@code majority}.
     * Nothing is checked when the server cannot be asked.
     */
    public static void warnIfWeakerThanMajority(MongoDatabase database, String property) {
        try {
            Document hello = database.runCommand(new Document("hello", 1));
            WriteConcern writeConcern = database.getWriteConcern();
            if (isWeakerThanMajority(writeConcern, hello) && log.isWarnEnabled()) {
                log.warn("CosId writes to replica set [{}] with write concern [{}], weaker than majority: a failover can roll back a machine id "
                    + "lease or a segment increment that was already handed out. Set {}=majority.", hello.get("setName"), writeConcern.asDocument(),
                    property);
            }
        } catch (RuntimeException checkFailure) {
            if (log.isDebugEnabled()) {
                log.debug("Can not check the write concern against the replica set.", checkFailure);
            }
        }
    }
    
    /**
     * Whether a write concern, on the server that answered {@code hello}, is acknowledged by fewer than a majority of
     * the replica set. An unset {@code w} is the server's default (majority since MongoDB 5.0), a tag set is not judged.
     */
    static boolean isWeakerThanMajority(@Nullable WriteConcern writeConcern, @Nullable Document hello) {
        if (writeConcern == null || hello == null || hello.get("setName") == null) {
            return false;
        }
        Object w = writeConcern.getWObject();
        if (!(w instanceof Integer members)) {
            return false;
        }
        List<?> hosts = hello.getList("hosts", Object.class, List.of());
        int majority = Math.max(1, hosts.size()) / 2 + 1;
        return members < majority;
    }
}
