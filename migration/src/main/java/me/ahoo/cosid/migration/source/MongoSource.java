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

package me.ahoo.cosid.migration.source;

import me.ahoo.cosid.migration.StoreLocation;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The 4.x MongoDB store (09 §4.1): segments in the collection {@code cosid}, {@code {_id: "{ns}.{name}", lastMaxId}};
 * machine ids in {@code cosid_machine}, {@code {_id: "{ns}.{machineId}", namespace, lastTimestamp, distributeTime,
 * revertTime, ...}}. The database is {@code options.database}, {@code cosid_db} by default.
 */
public final class MongoSource implements SourceReader {
    static final String SEGMENTS = "cosid";
    static final String MACHINES = "cosid_machine";
    private static final String LAST_MAX_ID = "lastMaxId";
    private static final List<String> TIMESTAMPS = List.of("lastTimestamp", "distributeTime", "revertTime");
    private final MongoClient client;
    private final MongoDatabase database;

    public MongoSource(StoreLocation location) {
        this.client = client(location);
        this.database = client.getDatabase(database(location));
    }

    /**
     * A client of a location: its connection string, with the credential of options {@code user} / {@code password}
     * (and {@code authSource}, the database of the URL or {@code admin} by default) when a password option is given, so
     * the password never needs to be in the URL that {@code plan.json} keeps.
     *
     * @param location the location
     * @return the client; the caller closes it
     */
    public static MongoClient client(StoreLocation location) {
        ConnectionString connection = new ConnectionString(location.url());
        MongoClientSettings.Builder settings = MongoClientSettings.builder().applyConnectionString(connection);
        String password = location.option("password");
        if (password != null) {
            String user = location.option("user") != null ? location.option("user") : connection.getUsername();
            if (user == null) {
                throw new IllegalArgumentException("Option password needs option user or a user in the URL " + StoreLocation.redactUrl(location.url()));
            }
            String source = location.option("authSource") != null ? location.option("authSource")
                : connection.getCredential() != null ? connection.getCredential().getSource()
                : connection.getDatabase() != null ? connection.getDatabase() : "admin";
            settings.credential(MongoCredential.createCredential(user, source, password.toCharArray()));
        }
        return MongoClients.create(settings.build());
    }

    /**
     * The database of a location: {@code options.database}, {@code cosid_db} by default.
     *
     * @param location the location
     * @return the database name
     */
    public static String database(StoreLocation location) {
        String name = location.option("database");
        return name == null ? "cosid_db" : name;
    }

    @Override
    public SourceSnapshot read(String namespace) {
        String prefix = namespace + ".";
        Map<String, Long> segments = new TreeMap<>();
        List<String> unrecognized = new ArrayList<>();
        for (Document document : database.getCollection(SEGMENTS).find(Filters.regex("_id", "^" + Pattern.quote(prefix)))) {
            String id = String.valueOf(document.get("_id"));
            Object value = document.get(LAST_MAX_ID);
            if (value instanceof Number number) {
                segments.put(id.substring(prefix.length()), number.longValue());
            } else {
                unrecognized.add(id + ": no lastMaxId (" + value + ")");
            }
        }
        Map<String, String> machines = new TreeMap<>();
        long maxTimestamp = 0;
        for (Document document : database.getCollection(MACHINES).find(Filters.eq("namespace", namespace))) {
            machines.put(String.valueOf(document.get("_id")), document.toJson());
            for (String field : TIMESTAMPS) {
                if (document.get(field) instanceof Number number) {
                    maxTimestamp = Math.max(maxTimestamp, number.longValue());
                }
            }
        }
        return new SourceSnapshot(segments, machines, maxTimestamp, unrecognized);
    }

    @Override
    public long seal(String namespace, String name, long increment) {
        String id = namespace + "." + name;
        Document current = database.getCollection(SEGMENTS).find(Filters.eq("_id", id)).first();
        if (current == null || !(current.get(LAST_MAX_ID) instanceof Number value)) {
            throw new IllegalStateException("Segment [" + id + "] does not exist");
        }
        SourceReader.sealed(id, value.longValue(), increment);
        Document sealed = database.getCollection(SEGMENTS).findOneAndUpdate(Filters.eq("_id", id), Updates.inc(LAST_MAX_ID, increment),
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (sealed == null) {
            throw new IllegalStateException("Segment [" + id + "] disappeared while sealing");
        }
        return ((Number) sealed.get(LAST_MAX_ID)).longValue();
    }

    @Override
    public long now() {
        Document now = database.aggregate(List.of(new Document("$documents", List.of(new Document())),
            new Document("$project", new Document("now", new Document("$toLong", "$$NOW"))))).first();
        return now == null ? 0 : ((Number) now.get("now")).longValue();
    }

    @Override
    public void close() {
        client.close();
    }
}
