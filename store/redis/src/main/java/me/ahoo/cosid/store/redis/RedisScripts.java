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

package me.ahoo.cosid.store.redis;

import java.util.List;

/**
 * The minimal way the Redis store talks to Redis (06-stores.md §2.2, D6): run a Lua script. {@link LettuceRedisScripts}
 * implements it on Lettuce; the Spring Data adapter of {@code spring/autoconfigure} implements it on a
 * {@code RedisTemplate}. Every key of one call shares a hash tag, so the call routes to one Redis Cluster node.
 *
 * <p>An implementation must not return before the write of the script is <em>acknowledged</em> as far as the
 * deployment requires (06 §2.2): with replicas, the store relies on it to run {@code WAIT} after the script on the same
 * connection and to fail the call when the replicas do not acknowledge in time.
 */
@FunctionalInterface
public interface RedisScripts {
    /**
     * Runs a script, by its SHA-1 digest when Redis has it cached and by its text otherwise ({@code NOSCRIPT}).
     *
     * @param sha    the lower case hex SHA-1 digest of {@code script}
     * @param script the Lua script
     * @param keys   the keys, {@code KEYS} of the script
     * @param args   the arguments, {@code ARGV} of the script
     * @return the multi-bulk reply of the script; bulk strings as {@link String}, integers as {@link Long}
     * @throws RedisStoreException if Redis fails, times out or does not acknowledge the write
     */
    List<Object> eval(String sha, String script, List<String> keys, List<String> args);
}
