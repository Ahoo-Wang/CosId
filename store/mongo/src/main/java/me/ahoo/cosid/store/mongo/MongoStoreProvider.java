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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import java.util.Objects;

/**
 * Opens {@link MongoStore}s, type {@code mongo} (06-stores.md §1); registered for {@link java.util.ServiceLoader}.
 *
 * <p>Options: {@code database}, the database name ({@value MongoStore#DEFAULT_DATABASE} by default); {@code uri}, the
 * connection string, required unless {@link StoreConfig#client()} holds a {@link MongoClient}. An injected client stays
 * open when the store closes; one built from {@code uri} is closed with it. {@link StoreConfig#timeout()} becomes the
 * driver's operation timeout ({@code timeoutMS}).
 */
public final class MongoStoreProvider implements StoreProvider {
    /** The store type. */
    public static final String TYPE = "mongo";
    /** The option naming the database. */
    public static final String DATABASE = "database";
    /** The option holding the connection string. */
    public static final String URI = "uri";

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
        if (config.client().isPresent()) {
            if (!(config.client().get() instanceof MongoClient client)) {
                throw new IllegalArgumentException("store " + config.name() + ": the client must be a " + MongoClient.class.getName()
                    + ", not " + config.client().get().getClass().getName());
            }
            return new MongoStore(client.getDatabase(database), config.timeout());
        }
        String uri = option(config, URI);
        if (uri == null) {
            throw new IllegalArgumentException("store " + config.name() + ": set options." + URI + " or inject a MongoClient");
        }
        MongoClient client = MongoClients.create(uri);
        try {
            return new MongoStore(client.getDatabase(database), config.timeout(), client);
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
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
