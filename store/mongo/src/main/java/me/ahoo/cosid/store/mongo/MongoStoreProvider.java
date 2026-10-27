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

import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Opens {@link MongoStore}s, type {@code mongo} (06-stores.md §1); registered for {@link java.util.ServiceLoader}.
 *
 * <p>Options: {@code database}, the database name ({@value MongoStore#DEFAULT_DATABASE} by default); {@code uri}, the
 * connection string, required unless {@link StoreConfig#client()} holds a {@link MongoClient}. An injected client stays
 * open when the store closes; one built from {@code uri} is closed with it; {@code writeConcern}, the write concern
 * ({@code majority} by default, see {@link #writeConcern(String, Object)}). {@link StoreConfig#timeout()} becomes the
 * driver's operation timeout ({@code timeoutMS}).
 */
public final class MongoStoreProvider implements StoreProvider {
    /** The store type. */
    public static final String TYPE = "mongo";
    /** The option naming the database. */
    public static final String DATABASE = "database";
    /** The option holding the connection string. */
    public static final String URI = "uri";
    /** The option holding the write concern. */
    public static final String WRITE_CONCERN = "writeConcern";

    private static final Logger log = LoggerFactory.getLogger(MongoStoreProvider.class);

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Store open(StoreConfig config) {
        Objects.requireNonNull(config, "config");
        String database = option(config, DATABASE);
        if (database == null) {
            database = MongoStore.DEFAULT_DATABASE;
        }
        WriteConcern writeConcern = writeConcern(config.name(), config.options().get(WRITE_CONCERN));
        if (MongoStore.isWeakerThanMajority(writeConcern)) {
            log.warn("Store [{}] - options.{} is {}, weaker than majority: the uniqueness of the ids rests on \"an acknowledged write "
                + "is never lost\", and a failover can lose range and lease writes acknowledged by fewer than a majority, and so "
                + "hand out duplicate ids", config.name(), WRITE_CONCERN, config.options().get(WRITE_CONCERN));
        }
        if (config.client().isPresent()) {
            if (!(config.client().get() instanceof MongoClient client)) {
                throw new IllegalArgumentException("store " + config.name() + ": the client must be a " + MongoClient.class.getName()
                    + ", not " + config.client().get().getClass().getName());
            }
            return new MongoStore(client.getDatabase(database), config.timeout(), writeConcern);
        }
        String uri = option(config, URI);
        if (uri == null) {
            throw new IllegalArgumentException("store " + config.name() + ": set options." + URI + " or inject a MongoClient");
        }
        MongoClient client = MongoClients.create(uri);
        try {
            return new MongoStore(client.getDatabase(database), config.timeout(), writeConcern, client);
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
    }

    /**
     * Parses {@code options.writeConcern}: absent means {@code majority}; otherwise one of the standard names
     * {@code majority}, {@code acknowledged}, {@code w1}, {@code w2}, {@code w3}, {@code journaled} (any case), or a
     * positive number of members ({@code w: n}). Unacknowledged concerns ({@code unacknowledged}, {@code 0}) and
     * anything else are rejected.
     *
     * @param store the store name, for the messages
     * @param value the option value, a string or a number; {@code null} when not set
     * @return the write concern
     */
    static WriteConcern writeConcern(String store, Object value) {
        if (value == null) {
            return WriteConcern.MAJORITY;
        }
        String text = value instanceof Number || value instanceof String ? value.toString().trim() : "";
        if (!text.isEmpty() && text.chars().allMatch(Character::isDigit)) {
            int members;
            try {
                members = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                members = 0;
            }
            if (members > 0) {
                return new WriteConcern(members);
            }
        } else if (!text.isEmpty()) {
            WriteConcern named = WriteConcern.valueOf(text);
            if (named != null && named.isAcknowledged()) {
                return named;
            }
        }
        throw new IllegalArgumentException("store " + store + ": options." + WRITE_CONCERN + " must be majority, acknowledged, w1, w2, w3, "
            + "journaled or a positive number of members (an unacknowledged write cannot keep ids unique): " + value);
    }

    private static String option(StoreConfig config, String name) {
        Object value = config.options().get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("store " + config.name() + ": options." + name + " must be a non-blank string: " + value);
        }
        return text;
    }
}
