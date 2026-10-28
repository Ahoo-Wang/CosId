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

package me.ahoo.cosid.store.jdbc;

import java.util.function.Function;

/**
 * One statement of a dialect, built from the table names on first use and kept while the stores use the same names
 * (R1-jdbc-02): a dialect is shared, its stores' names are fixed, so a reserve does not rebuild its SQL every call.
 */
final class TableSql {
    private final Function<JdbcTables, String> build;
    private volatile Built last;

    TableSql(Function<JdbcTables, String> build) {
        this.build = build;
    }

    String of(JdbcTables tables) {
        Built built = last;
        if (built == null || built.tables != tables && !built.tables.equals(tables)) {
            built = new Built(tables, build.apply(tables));
            last = built;
        }
        return built.sql;
    }

    private record Built(JdbcTables tables, String sql) {
    }
}
