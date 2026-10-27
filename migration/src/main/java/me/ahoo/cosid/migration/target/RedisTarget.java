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

package me.ahoo.cosid.migration.target;

import me.ahoo.cosid.migration.RedisConnection;
import me.ahoo.cosid.migration.StoreLocation;
import me.ahoo.cosid.store.redis.RedisStore;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The 5.0 Redis store (06 §2.2): a range is {@code cosid:{key}} with its seal marker {@code cosid:{key}:sealed}; a
 * namespace is {@code cosid:{ns}:meta}, {@code :slot} (slot rows, encoded by store/redis {@code slot-row.lua}, which the
 * slot script here runs first), {@code :free} and {@code :live}.
 */
public final class RedisTarget implements TargetAdmin {
    private static final String RAISE = resource(RedisTarget.class, "redis-raise.lua");
    private static final String PREPARE = resource(RedisStore.class, "slot-row.lua") + "\n" + resource(RedisTarget.class, "redis-prepare.lua");
    private static final String SEALED = "}:sealed";
    private final RedisConnection redis;

    public RedisTarget(StoreLocation location) {
        this.redis = RedisConnection.open(location);
    }

    static String resource(Class<?> owner, String name) {
        try (InputStream in = owner.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + name + " of " + owner.getName());
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private RedisClusterCommands<String, String> commands() {
        return redis.commands();
    }

    private static String rangeKey(String key) {
        return "cosid:{" + key + "}";
    }

    private static String nsKey(String namespace, String part) {
        return "cosid:{" + namespace + "}:" + part;
    }

    @Override
    public long now() {
        List<String> time = commands().time();
        return Long.parseLong(time.get(0)) * 1000 + Long.parseLong(time.get(1)) / 1000;
    }

    @Override
    public Optional<RangeState> range(String key) {
        String value = commands().get(rangeKey(key));
        boolean sealed = commands().exists(rangeKey(key) + ":sealed") > 0;
        if (value == null) {
            return sealed ? Optional.of(new RangeState(key, 0, true)) : Optional.empty();
        }
        return Optional.of(new RangeState(key, Long.parseLong(value), sealed));
    }

    @Override
    public RangeState raise(String key, long next) {
        if (next < 0) {
            throw new IllegalArgumentException("next must not be negative: " + next);
        }
        List<Object> reply = commands().eval(RAISE, ScriptOutputType.MULTI, new String[]{rangeKey(key), rangeKey(key) + ":sealed"}, Long.toString(next));
        return new RangeState(key, Long.parseLong(String.valueOf(reply.get(0))), "1".equals(String.valueOf(reply.get(1))));
    }

    @Override
    public List<RangeState> ranges(String prefix) {
        String keyPrefix = "cosid:{";
        // A key sealed before it was ever used has its seal marker only.
        Set<String> keys = new TreeSet<>();
        for (String redisKey : redis.scan(RedisConnection.globEscape(keyPrefix + prefix) + "*")) {
            if (redisKey.endsWith(SEALED)) {
                keys.add(redisKey.substring(keyPrefix.length(), redisKey.length() - SEALED.length()));
            } else if (redisKey.endsWith("}")) {
                keys.add(redisKey.substring(keyPrefix.length(), redisKey.length() - 1));
            }
        }
        List<RangeState> ranges = new ArrayList<>();
        for (String key : keys) {
            range(key).ifPresent(ranges::add);
        }
        return ranges;
    }

    @Override
    public boolean delete(String key) {
        if (commands().exists(rangeKey(key) + ":sealed") == 0) {
            return false;
        }
        commands().del(rangeKey(key));
        commands().del(rangeKey(key) + ":sealed");
        return true;
    }

    @Override
    public Optional<NamespaceState> namespace(String namespace) {
        Map<String, String> meta = commands().hgetall(nsKey(namespace, "meta"));
        if (meta == null || meta.get("limit") == null) {
            return Optional.empty();
        }
        int limit = Integer.parseInt(meta.get("limit"));
        long tolerance = Long.parseLong(meta.get("tolerance"));
        Map<String, String> rows = commands().hgetall(nsKey(namespace, "slot"));
        int slots = 0;
        long minNextFloor = Long.MAX_VALUE;
        for (int slot = 0; slot < limit; slot++) {
            String row = rows.get(Integer.toString(slot));
            boolean exists = row != null || commands().zscore(nsKey(namespace, "free"), Integer.toString(slot)) != null
                || commands().zscore(nsKey(namespace, "live"), Integer.toString(slot)) != null;
            if (exists) {
                slots++;
                minNextFloor = Math.min(minNextFloor, row == null ? 0 : nextFloor(row));
            }
        }
        long live = commands().zcount(nsKey(namespace, "live"), io.lettuce.core.Range.create((double) now(), Double.POSITIVE_INFINITY));
        return Optional.of(new NamespaceState(limit, tolerance, slots, minNextFloor, Math.toIntExact(live)));
    }

    /** The next floor of a slot row {@code "ownerKey|incarnation|lastOwnerKey|fence|expiresAt|floor|ceiling|nextFloor"}. */
    static long nextFloor(String row) {
        String[] fields = row.split("\\|", -1);
        return Long.parseLong(fields[7]);
    }

    @Override
    public NamespaceState prepare(String namespace, int limit, long toleranceMillis, long nextFloor) {
        List<Object> reply = commands().eval(PREPARE, ScriptOutputType.MULTI,
            new String[]{nsKey(namespace, "meta"), nsKey(namespace, "slot"), nsKey(namespace, "free"), nsKey(namespace, "live")},
            Integer.toString(limit), Long.toString(toleranceMillis), Long.toString(nextFloor));
        String status = String.valueOf(reply.get(0));
        if ("mismatch".equals(status)) {
            throw TargetAdmin.mismatch(namespace, limit, toleranceMillis, Integer.parseInt(String.valueOf(reply.get(1))), Long.parseLong(String.valueOf(reply.get(2))));
        }
        if ("live".equals(status)) {
            throw TargetAdmin.live(namespace, Integer.parseInt(String.valueOf(reply.get(1))));
        }
        return namespace(namespace).orElseThrow();
    }

    @Override
    public void close() {
        redis.close();
    }
}
