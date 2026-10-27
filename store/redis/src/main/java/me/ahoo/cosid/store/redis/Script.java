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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * A Lua script of the store and its SHA-1 digest, loaded from the resources of this package.
 *
 * @param name   the resource name without {@code .lua}
 * @param text   the script
 * @param sha    the lower case hex SHA-1 digest of the script
 */
record Script(String name, String text, String sha) {
    static final Script RESERVE = load("reserve");
    static final Script SEAL = load("seal");
    static final Script ACQUIRE = load("slot-row", "acquire");
    static final Script RENEW = load("slot-row", "renew");
    static final Script RELEASE = load("slot-row", "release");

    /** Loads the resources one after the other into one script, named after the last one. */
    private static Script load(String... resources) {
        StringBuilder text = new StringBuilder();
        for (String resource : resources) {
            try (InputStream in = Script.class.getResourceAsStream(resource + ".lua")) {
                if (in == null) {
                    throw new IllegalStateException("Missing script resource " + resource + ".lua");
                }
                text.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        String script = text.toString();
        return new Script(resources[resources.length - 1], script, sha1(script));
    }

    static String sha1(String script) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(script.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    List<String> eval(RedisScripts scripts, List<String> keys, List<String> args) {
        List<Object> reply = scripts.eval(sha, text, keys, args);
        if (reply == null || reply.isEmpty()) {
            throw new RedisStoreException("Script " + name + " returned no reply");
        }
        return reply.stream().map(String::valueOf).toList();
    }
}
