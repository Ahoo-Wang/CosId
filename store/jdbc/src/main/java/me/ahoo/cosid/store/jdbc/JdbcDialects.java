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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * The built-in {@link JdbcDialect}s (06-stores.md §2.1) and the detection of the dialect of a database.
 */
public final class JdbcDialects {
    /** MySQL 8.4 LTS and later. */
    public static final JdbcDialect MYSQL = new MySqlDialect(false);
    /** MariaDB 10.11 LTS and later. */
    public static final JdbcDialect MARIADB = new MySqlDialect(true);
    /** PostgreSQL 15 and later. */
    public static final JdbcDialect POSTGRESQL = new PostgreSqlDialect();
    /** SQL Server 2019 and later. */
    public static final JdbcDialect SQL_SERVER = new SqlServerDialect();
    /** H2 2.x, for development and tests. */
    public static final JdbcDialect H2 = new H2Dialect();

    private static final List<JdbcDialect> BUILT_IN = List.of(MARIADB, MYSQL, POSTGRESQL, SQL_SERVER, H2);

    private JdbcDialects() {
    }

    /**
     * The dialects to choose from: those registered with {@link ServiceLoader} first, then the built-in ones.
     *
     * @param classLoader the class loader of the {@link ServiceLoader} lookup
     * @return the dialects
     */
    public static List<JdbcDialect> available(ClassLoader classLoader) {
        List<JdbcDialect> dialects = new ArrayList<>();
        ServiceLoader.load(JdbcDialect.class, classLoader).forEach(dialects::add);
        dialects.addAll(BUILT_IN);
        return dialects;
    }

    /**
     * The dialect of the given name among {@link #available}, ignoring case.
     *
     * @param name        the name, such as {@code mysql}
     * @param classLoader the class loader of the {@link ServiceLoader} lookup
     * @return the dialect, if there is one of this name
     */
    public static Optional<JdbcDialect> named(String name, ClassLoader classLoader) {
        return available(classLoader).stream().filter(dialect -> dialect.name().equalsIgnoreCase(name)).findFirst();
    }

    /**
     * The first of {@link #available} that supports the database of the connection.
     *
     * @param connection  a connection to the database
     * @param classLoader the class loader of the {@link ServiceLoader} lookup
     * @return the dialect
     * @throws SQLException             if the metadata cannot be read
     * @throws IllegalArgumentException if no dialect supports the database
     */
    public static JdbcDialect detect(Connection connection, ClassLoader classLoader) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        for (JdbcDialect dialect : available(classLoader)) {
            if (dialect.supports(metaData)) {
                return dialect;
            }
        }
        throw new IllegalArgumentException("No JDBC dialect for " + metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion()
            + "; set options.dialect or register a JdbcDialect with ServiceLoader");
    }

    static boolean productIs(DatabaseMetaData metaData, String product) throws SQLException {
        return metaData.getDatabaseProductName().toLowerCase(Locale.ROOT).contains(product);
    }

    /**
     * MariaDB servers answer to the MySQL driver as {@code MySQL}; their version string says {@code MariaDB}.
     */
    static boolean isMariaDb(DatabaseMetaData metaData) throws SQLException {
        return productIs(metaData, "mariadb") || metaData.getDatabaseProductVersion().toLowerCase(Locale.ROOT).contains("mariadb");
    }

    /**
     * The column list and placeholders of a {@code cosid_namespace} insert:
     * {@code (namespace, slot_limit, tolerance_ms[, slot_offset]) values (?, ?, ?[, ?])}. A slot offset of 0 is left to
     * the column default, so that the insert also runs on a table of 5.0.0-alpha.1 without {@code slot_offset}.
     *
     * @param slotOffset the slot offset
     * @return the column list and the {@code values} clause
     */
    static String namespaceInsert(int slotOffset) {
        return slotOffset == 0
            ? "(namespace, slot_limit, tolerance_ms) values (?, ?, ?)"
            : "(namespace, slot_limit, tolerance_ms, slot_offset) values (?, ?, ?, ?)";
    }

    /**
     * Binds the parameters of {@link #namespaceInsert}, or of a statement with the same parameters in the same order.
     */
    static void bindNamespace(PreparedStatement statement, String namespace, int limit, long toleranceMillis, int slotOffset) throws SQLException {
        statement.setString(1, namespace);
        statement.setInt(2, limit);
        statement.setLong(3, toleranceMillis);
        if (slotOffset != 0) {
            statement.setInt(4, slotOffset);
        }
    }

    /**
     * The statements of a {@code schema-*.sql} resource of this package, renamed to the tables.
     */
    static List<String> schema(String resource, JdbcTables tables) {
        String script;
        try (InputStream in = JdbcDialects.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String statement = current.toString().strip();
                statements.add(tables.rename(statement.substring(0, statement.length() - 1)));
                current.setLength(0);
            }
        }
        return List.copyOf(statements);
    }
}
