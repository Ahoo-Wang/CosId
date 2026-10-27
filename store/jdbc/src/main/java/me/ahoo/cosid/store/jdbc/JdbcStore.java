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

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * The JDBC store (06-stores.md §2.1): ranges and slot leases over a {@link DataSource}, which should be pooled. Every
 * statement carries the store timeout as its {@code queryTimeout}. Closing the store does not close the data source.
 *
 * <p>The tables are those of the dialect's {@code schema-*.sql}; {@link #initSchema()} creates them when missing.
 */
public final class JdbcStore implements Store {
    private final Jdbc jdbc;
    private final JdbcRangeStore ranges;
    private final JdbcSlotLeaseStore slots;

    /**
     * A store on a database of the given dialect.
     *
     * @param dataSource the data source
     * @param dialect    the dialect of its database
     * @param tables     the table names
     * @param timeout    the timeout of each statement, rounded up to seconds; zero for none
     */
    public JdbcStore(DataSource dataSource, JdbcDialect dialect, JdbcTables tables, Duration timeout) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(tables, "tables");
        Objects.requireNonNull(timeout, "timeout");
        this.jdbc = new Jdbc(dataSource, dialect, tables, timeout);
        this.ranges = new JdbcRangeStore(jdbc);
        this.slots = new JdbcSlotLeaseStore(jdbc);
    }

    /**
     * A store on the default tables, with the dialect detected from the database ({@link JdbcDialects#detect}).
     *
     * @param dataSource the data source
     * @param timeout    the timeout of each statement; zero for none
     * @return the store
     * @throws JdbcStoreException       if the database cannot be reached
     * @throws IllegalArgumentException if no dialect supports the database
     */
    public static JdbcStore of(DataSource dataSource, Duration timeout) {
        return new JdbcStore(dataSource, detect(dataSource, JdbcStore.class.getClassLoader()), JdbcTables.DEFAULT, timeout);
    }

    static JdbcDialect detect(DataSource dataSource, ClassLoader classLoader) {
        try (Connection connection = dataSource.getConnection()) {
            return JdbcDialects.detect(connection, classLoader);
        } catch (SQLException e) {
            throw new JdbcStoreException("Detecting the JDBC dialect failed", e);
        }
    }

    /**
     * The dialect of the database.
     *
     * @return the dialect
     */
    public JdbcDialect dialect() {
        return jdbc.dialect;
    }

    /**
     * The table names.
     *
     * @return the table names
     */
    public JdbcTables tables() {
        return jdbc.tables;
    }

    /**
     * Creates the tables that do not exist ({@code options.initSchema}); existing tables are left as they are.
     *
     * @throws JdbcStoreException if a statement fails
     */
    public void initSchema() {
        jdbc.run("init schema", session -> {
            for (String ddl : jdbc.dialect.schema(jdbc.tables)) {
                try (Statement statement = session.connection().createStatement()) {
                    statement.setQueryTimeout(jdbc.queryTimeoutSeconds);
                    statement.execute(ddl);
                }
            }
            return null;
        });
    }

    @Override
    public RangeStore ranges() {
        return ranges;
    }

    @Override
    public SlotLeaseStore slots() {
        return slots;
    }

    @Override
    public void close() {
    }
}
