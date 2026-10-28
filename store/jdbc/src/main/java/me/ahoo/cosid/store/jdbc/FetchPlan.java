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

import java.util.Locale;

/**
 * How a JDBC store learns the {@code next_value} a reserve wrote ({@code options.fetchPlan}, R1-jdbc-06; 4.x
 * {@code fetch-plan}).
 */
public enum FetchPlan {
    /**
     * From the statement that wrote it, in one round trip: PostgreSQL {@code returning}, SQL Server {@code output},
     * MySQL/MariaDB the {@code last_insert_id} the statement sets, which the driver returns as its generated key. The
     * default. A generated key the statement did not set (an SQL proxy that rewrites or drops it) fails the reserve
     * when it is implausible; when the key is missing, the store never asks the connection for it.
     */
    STATEMENT,
    /**
     * In one transaction: an update of the row, then a read of it, which the row lock keeps exact. One more round
     * trip, but nothing depends on connection state, so it works behind SQL proxies and read-write splitting, which
     * keep a transaction on the primary.
     */
    TRANSACTION;

    /**
     * The plan an option names, ignoring case and {@code -} / {@code _}.
     *
     * @param value the option value; null for the default
     * @return the plan
     * @throws IllegalArgumentException if the value names no plan
     */
    public static FetchPlan of(Object value) {
        if (value == null) {
            return STATEMENT;
        }
        String name = value.toString().trim().replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
        for (FetchPlan plan : values()) {
            if (plan.name().equals(name)) {
                return plan;
            }
        }
        throw new IllegalArgumentException("options.fetchPlan must be statement or transaction: " + value);
    }
}
