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

package me.ahoo.cosid.migration;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a store is: its type, the URL the operator gave and options (09 §3 {@code --from} / {@code --to}).
 *
 * <ul>
 *     <li>{@code jdbc:...}: a JDBC URL; options {@code user}, {@code password} and, for a 5.0 target,
 *     {@code rangeTable} / {@code slotTable} / {@code namespaceTable}, {@code initSchema};</li>
 *     <li>{@code redis://}, {@code rediss://}: a Lettuce URI; option {@code cluster=true} for Redis Cluster;</li>
 *     <li>{@code mongodb://}, {@code mongodb+srv://}: a MongoDB connection string; option {@code database}
 *     ({@code cosid_db} by default, as in 4.x and 5.0);</li>
 *     <li>{@code zookeeper://host:port[,host:port][/chroot]}: a ZooKeeper connect string.</li>
 * </ul>
 *
 * <p>Passwords are never written to {@code plan.json}: give them as {@code --from-option password=...} or in the
 * environment variable {@code COSID_MIGRATION_FROM_PASSWORD} / {@code COSID_MIGRATION_TO_PASSWORD} on every command.
 *
 * @param type    {@code jdbc}, {@code redis}, {@code mongo} or {@code zookeeper}
 * @param url     the URL as given
 * @param options the options
 */
public record StoreLocation(String type, String url, Map<String, String> options) {
    public static final String JDBC = "jdbc";
    public static final String REDIS = "redis";
    public static final String MONGO = "mongo";
    public static final String ZOOKEEPER = "zookeeper";

    public StoreLocation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(url, "url");
        options = Map.copyOf(options == null ? Map.of() : options);
    }

    /**
     * Parses a location, the type from the URL scheme.
     *
     * @param url     the URL
     * @param options the options
     * @return the location
     * @throws IllegalArgumentException for an unknown scheme
     */
    public static StoreLocation parse(String url, Map<String, String> options) {
        Objects.requireNonNull(url, "url");
        String lower = url.toLowerCase(Locale.ROOT);
        String type;
        if (lower.startsWith("jdbc:")) {
            type = JDBC;
        } else if (lower.startsWith("redis://") || lower.startsWith("rediss://")) {
            type = REDIS;
        } else if (lower.startsWith("mongodb://") || lower.startsWith("mongodb+srv://")) {
            type = MONGO;
        } else if (lower.startsWith("zookeeper://")) {
            type = ZOOKEEPER;
        } else {
            throw new IllegalArgumentException("Unknown store [" + url + "]: expected jdbc:..., redis://, rediss://, mongodb://, mongodb+srv:// or zookeeper://");
        }
        return new StoreLocation(type, url, options);
    }

    /**
     * The option, or null.
     *
     * @param name the option name
     * @return the value
     */
    public String option(String name) {
        return options.get(name);
    }

    /**
     * The account: option {@code user}, or {@code username} as the 5.0 JDBC store names it (R1-doc-05).
     *
     * @return the account, or null
     * @throws IllegalArgumentException if both are given with different values
     */
    public String user() {
        String user = options.get("user");
        String username = options.get("username");
        if (user != null && username != null && !user.equals(username)) {
            throw new IllegalArgumentException("Options user [" + user + "] and username [" + username + "] differ: give one of them");
        }
        return user != null ? user : username;
    }

    /**
     * This location with the password from the environment when no option gives one.
     *
     * @param side {@code FROM} or {@code TO}
     * @return the location
     */
    public StoreLocation withEnvironmentPassword(String side) {
        String password = System.getenv("COSID_MIGRATION_" + side + "_PASSWORD");
        if (password == null || options.containsKey("password")) {
            return this;
        }
        Map<String, String> withPassword = new TreeMap<>(options);
        withPassword.put("password", password);
        return new StoreLocation(type, url, withPassword);
    }

    /**
     * This location with more options, which win over existing ones.
     *
     * @param more the options
     * @return the location
     */
    public StoreLocation withOptions(Map<String, String> more) {
        if (more.isEmpty()) {
            return this;
        }
        Map<String, String> merged = new TreeMap<>(options);
        merged.putAll(more);
        return new StoreLocation(type, url, merged);
    }

    /**
     * The location as {@code plan.json} keeps it: without the password option.
     *
     * @return the location
     */
    @JsonIgnore
    public StoreLocation redacted() {
        Map<String, String> kept = new TreeMap<>(options);
        kept.keySet().removeIf(name -> name.toLowerCase(Locale.ROOT).contains("password"));
        return new StoreLocation(type, redactUrl(url), kept);
    }

    private static final Pattern USERINFO_PASSWORD = Pattern.compile("(//[^/@:?#]*):[^/@?#]*@");
    private static final Pattern PASSWORD_PARAMETER = Pattern.compile("([?&;])(?:password|pwd)=[^&;]*([&;]?)", Pattern.CASE_INSENSITIVE);

    /**
     * A URL without its secrets: the password of the userinfo ({@code //user:secret@host} becomes {@code //user@host})
     * and {@code password=} / {@code pwd=} parameters ({@code ?password=}, {@code &password=}, {@code ;password=}).
     *
     * @param url the URL
     * @return the URL without them
     */
    public static String redactUrl(String url) {
        String redacted = USERINFO_PASSWORD.matcher(url).replaceFirst("$1@");
        for (Matcher parameter = PASSWORD_PARAMETER.matcher(redacted); parameter.find(); parameter = PASSWORD_PARAMETER.matcher(redacted)) {
            // "?password=x&a=b" keeps "?a=b"; a parameter at the end goes with its separator.
            String keep = parameter.group(2).isEmpty() ? "" : parameter.group(1);
            redacted = redacted.substring(0, parameter.start()) + keep + redacted.substring(parameter.end());
        }
        return redacted;
    }

    /** The type and the url without its password ({@link #redactUrl}): messages and logs carry this. */
    @Override
    public String toString() {
        return type + " " + redactUrl(url);
    }
}
