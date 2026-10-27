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

import java.util.stream.Stream;

/**
 * A server backed by the JDBC store on an in-memory H2 database of its own.
 */
final class H2 {
    private H2() {
    }

    static String[] properties(String database, String... more) {
        Stream<String> store = Stream.of(
            "cosid.stores.main.type=jdbc",
            "cosid.stores.main.options.url=jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
            "cosid.stores.main.options.username=sa",
            "cosid.stores.main.options.initSchema=true");
        return Stream.concat(store, Stream.of(more)).toArray(String[]::new);
    }
}
