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

package me.ahoo.cosid.store.zookeeper;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Opens {@link ZookeeperStore}s, type {@value #TYPE} (06-stores.md §1). The client is the {@link CuratorFramework} in
 * {@link StoreConfig#client()}, which the caller owns; without one the provider builds and starts a client from the
 * options, which the store closes. The options replace 4.x {@code cosid.zookeeper.*}:
 *
 * <table>
 *     <caption>Options</caption>
 *     <tr><th>Option</th><th>Default</th><th>Meaning</th></tr>
 *     <tr><td>{@code connectString}</td><td>{@code localhost:2181}</td><td>the ZooKeeper ensemble</td></tr>
 *     <tr><td>{@code sessionTimeout}</td><td>60s</td><td>the session timeout</td></tr>
 *     <tr><td>{@code connectionTimeout}</td><td>15s</td><td>the connection timeout</td></tr>
 *     <tr><td>{@code blockUntilConnectedWait}</td><td>10s</td><td>how long {@code open} waits for the first connection</td></tr>
 *     <tr><td>{@code retry.baseSleepTime}</td><td>100ms</td><td>the first pause of Curator's exponential backoff retry</td></tr>
 *     <tr><td>{@code retry.maxRetries}</td><td>5</td><td>the retries of a connection loss</td></tr>
 *     <tr><td>{@code retry.maxSleep}</td><td>500ms</td><td>the longest pause between retries</td></tr>
 * </table>
 *
 * <p>Durations are a {@link Duration}, a number of milliseconds, or text: ISO-8601 ({@code PT5S}) or a number with
 * {@code ms}, {@code s}, {@code m} or {@code h}. The store timeout bounds every operation (Curator has no per-operation
 * timeout of its own).
 */
public final class ZookeeperStoreProvider implements StoreProvider {
    public static final String TYPE = "zookeeper";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Set<String> options() {
        return Set.of("connectString", "sessionTimeout", "connectionTimeout", "retry.baseSleepTime", "retry.maxSleep", "retry.maxRetries",
            "blockUntilConnectedWait");
    }

    @Override
    public Store open(StoreConfig config) {
        if (config.client().isPresent()) {
            Object client = config.client().get();
            if (!(client instanceof CuratorFramework curator)) {
                throw new IllegalArgumentException("Store [" + config.name() + "]: the zookeeper client must be a CuratorFramework, not "
                    + client.getClass().getName());
            }
            return new ZookeeperStore(curator, config.timeout());
        }
        Map<String, Object> options = config.options();
        Duration baseSleep = duration(options, "retry.baseSleepTime", Duration.ofMillis(100));
        Duration maxSleep = duration(options, "retry.maxSleep", Duration.ofMillis(500));
        CuratorFramework client = CuratorFrameworkFactory.builder()
            .connectString(string(options, "connectString", "localhost:2181"))
            .sessionTimeoutMs(Math.toIntExact(duration(options, "sessionTimeout", Duration.ofSeconds(60)).toMillis()))
            .connectionTimeoutMs(Math.toIntExact(duration(options, "connectionTimeout", Duration.ofSeconds(15)).toMillis()))
            .retryPolicy(new ExponentialBackoffRetry(Math.toIntExact(baseSleep.toMillis()), integer(options, "retry.maxRetries", 5),
                Math.toIntExact(maxSleep.toMillis())))
            .build();
        Duration wait = duration(options, "blockUntilConnectedWait", Duration.ofSeconds(10));
        try {
            client.start();
            if (!client.blockUntilConnected(Math.toIntExact(wait.toMillis()), TimeUnit.MILLISECONDS)) {
                throw new CosIdException("Store [" + config.name() + "]: no connection to ZooKeeper within " + wait);
            }
        } catch (InterruptedException e) {
            client.close();
            Thread.currentThread().interrupt();
            throw new CosIdException("Store [" + config.name() + "]: interrupted while connecting to ZooKeeper", e);
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
        return new ZookeeperStore(client, config.timeout(), true);
    }

    private static String string(Map<String, Object> options, String name, String defaultValue) {
        Object value = options.get(name);
        return value == null ? defaultValue : value.toString();
    }

    private static int integer(Map<String, Object> options, String name, int defaultValue) {
        Object value = options.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Option " + name + " is no integer: " + value, e);
        }
    }

    static Duration duration(Map<String, Object> options, String name, Duration defaultValue) {
        Object value = options.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Duration duration) {
            return duration;
        }
        if (value instanceof Number number) {
            return Duration.ofMillis(number.longValue());
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        try {
            if (text.startsWith("p") || text.startsWith("-p")) {
                return Duration.parse(text.toUpperCase(Locale.ROOT));
            }
            if (text.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(text.substring(0, text.length() - 2).trim()));
            }
            if (text.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(text.substring(0, text.length() - 1).trim()));
            }
            if (text.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(text.substring(0, text.length() - 1).trim()));
            }
            if (text.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(text.substring(0, text.length() - 1).trim()));
            }
            return Duration.ofMillis(Long.parseLong(text));
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException("Option " + name + " is no duration: " + value, e);
        }
    }
}
