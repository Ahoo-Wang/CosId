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

import java.util.List;
import java.util.Set;

/**
 * What a caller may use (08-remote.md §3): everything, or the namespaces and range key prefixes of its client token.
 *
 * @param name         the client, for logs
 * @param unrestricted whether it may use every namespace and key
 * @param namespaces   the namespaces; {@code *} allows all
 * @param keyPrefixes  the range key prefixes
 */
record Grant(String name, boolean unrestricted, Set<String> namespaces, List<String> keyPrefixes) {
    /** The request attribute holding the caller's grant. */
    static final String ATTRIBUTE = "me.ahoo.cosid.server.Grant";
    /** Calls without a token on a server that allows anonymous calls. */
    static final Grant ANONYMOUS = new Grant("anonymous", true, Set.of(), List.of());

    Grant {
        namespaces = Set.copyOf(namespaces);
        keyPrefixes = List.copyOf(keyPrefixes);
    }

    static Grant unrestricted(String name) {
        return new Grant(name, true, Set.of(), List.of());
    }

    /**
     * Whether it may use the slots of the namespace, or the id endpoints of a runtime in it.
     *
     * @param namespace the namespace
     * @return whether it may
     */
    boolean allowsNamespace(String namespace) {
        return unrestricted || namespaces.contains("*") || namespaces.contains(namespace);
    }

    /**
     * Whether it may use the range key: the key's namespace (its first segment) is allowed, or the key starts with a
     * prefix.
     *
     * @param key the range key
     * @return whether it may
     */
    boolean allowsKey(String key) {
        if (allowsNamespace(Requests.namespaceOf(key))) {
            return true;
        }
        for (String prefix : keyPrefixes) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
