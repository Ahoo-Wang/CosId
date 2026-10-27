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

package me.ahoo.cosid.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The tokens of {@code cosid.server.auth} and what each grants. Building it is where the server refuses to start
 * without a token (08-remote.md §3).
 */
final class Grants {
    private final List<Entry> entries;
    private final boolean anonymous;

    private record Entry(byte[] token, Grant grant) {
    }

    private Grants(List<Entry> entries, boolean anonymous) {
        this.entries = List.copyOf(entries);
        this.anonymous = anonymous;
    }

    static Grants of(ServerProperties.Auth auth) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = 0;
        for (String token : auth.tokens()) {
            requireToken(token, "tokens[" + index + "]", seen);
            entries.add(new Entry(bytes(token), Grant.unrestricted("tokens[" + index + "]")));
            index++;
        }
        index = 0;
        for (ServerProperties.Client client : auth.clients()) {
            String at = "clients[" + index + "]";
            String name = client.name() == null || client.name().isBlank() ? at : client.name();
            requireToken(client.token(), at + ".token", seen);
            if (client.namespaces().isEmpty() && client.keyPrefixes().isEmpty()) {
                throw new IllegalStateException(ServerProperties.PREFIX + ".auth." + at + " (" + name + "): give it namespaces or key-prefixes");
            }
            entries.add(new Entry(bytes(client.token()), new Grant(name, false, Set.copyOf(client.namespaces()), client.keyPrefixes())));
            index++;
        }
        if (entries.isEmpty() && !auth.allowAnonymous()) {
            throw new IllegalStateException("cosid-server refuses to start without authentication: set " + ServerProperties.PREFIX
                + ".auth.tokens or " + ServerProperties.PREFIX + ".auth.clients, or " + ServerProperties.PREFIX + ".auth.allow-anonymous=true");
        }
        return new Grants(entries, entries.isEmpty());
    }

    private static void requireToken(String token, String at, Set<String> seen) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(ServerProperties.PREFIX + ".auth." + at + " is blank");
        }
        if (!seen.add(token.trim())) {
            throw new IllegalStateException(ServerProperties.PREFIX + ".auth." + at + " repeats another token");
        }
    }

    private static byte[] bytes(String token) {
        return token.trim().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Whether calls need no token: no token is configured and anonymous calls are allowed.
     *
     * @return whether the server is open
     */
    boolean anonymous() {
        return anonymous;
    }

    /**
     * The grant of a token, compared in constant time against every configured token.
     *
     * @param token the bearer token
     * @return the grant, or empty for an unknown token
     */
    Optional<Grant> find(String token) {
        byte[] given = bytes(token);
        Grant found = null;
        for (Entry entry : entries) {
            if (MessageDigest.isEqual(entry.token(), given) && found == null) {
                found = entry.grant();
            }
        }
        return Optional.ofNullable(found);
    }
}
