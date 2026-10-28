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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import javax.sql.DataSource;

/**
 * The databases of the tests, at their minimum supported versions (06-stores.md §2.1, D27) unless
 * {@code COSID_TEST_<NAME>_IMAGE} names another image. A database comes from
 * {@code COSID_TEST_<NAME>_URL} with {@code _USERNAME} / {@code _PASSWORD} (or the system properties
 * {@code cosid.test.<name>.url} …) when set, otherwise from a Testcontainers container shared by the test JVM; a test
 * class skips itself when neither the variable nor Docker is there ({@link #assumeAvailable()}). H2 is in memory.
 */
enum TestDatabase {
    H2(JdbcDialects.H2, null),
    MYSQL(JdbcDialects.MYSQL, () -> new MySQLContainer(image("MYSQL", "mysql:8.4"))),
    MARIADB(JdbcDialects.MARIADB, () -> new MariaDBContainer(image("MARIADB", "mariadb:10.11"))),
    POSTGRES(JdbcDialects.POSTGRESQL, () -> new PostgreSQLContainer(image("POSTGRES", "postgres:15-alpine"))),
    SQLSERVER(JdbcDialects.SQL_SERVER, () -> new MSSQLServerContainer(image("SQLSERVER", "mcr.microsoft.com/mssql/server:2019-latest")).acceptLicense());

    /** The statement timeout of test stores. */
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final JdbcDialect dialect;
    private final Supplier<JdbcDatabaseContainer<?>> container;
    private volatile HikariDataSource dataSource;

    TestDatabase(JdbcDialect dialect, Supplier<JdbcDatabaseContainer<?>> container) {
        this.dialect = dialect;
        this.container = container;
    }

    private static String image(String name, String minimum) {
        String image = System.getenv("COSID_TEST_" + name + "_IMAGE");
        return image == null || image.isBlank() ? minimum : image;
    }

    JdbcDialect dialect() {
        return dialect;
    }

    private Optional<String> setting(String suffix) {
        String name = name().toLowerCase(Locale.ROOT);
        String property = System.getProperty("cosid.test." + name + "." + suffix.toLowerCase(Locale.ROOT));
        if (property != null && !property.isBlank()) {
            return Optional.of(property);
        }
        String env = System.getenv("COSID_TEST_" + name() + "_" + suffix);
        return env == null || env.isBlank() ? Optional.empty() : Optional.of(env);
    }

    boolean isAvailable() {
        return container == null || setting("URL").isPresent() || DockerClientFactory.instance().isDockerAvailable();
    }

    void assumeAvailable() {
        Assumptions.assumeTrue(isAvailable(), () -> name() + ": set COSID_TEST_" + name() + "_URL or start Docker");
    }

    /**
     * The pooled data source of the database, its schema created.
     */
    DataSource dataSource() {
        HikariDataSource current = dataSource;
        if (current == null) {
            synchronized (this) {
                current = dataSource;
                if (current == null) {
                    current = open();
                    new JdbcStore(current, dialect, JdbcTables.DEFAULT, TIMEOUT).initSchema();
                    dataSource = current;
                }
            }
        }
        return current;
    }

    /**
     * A connection setting of the database: {@code url}, {@code username} and {@code password}.
     */
    String[] connectionSettings() {
        if (container == null) {
            return new String[] {"jdbc:h2:mem:cosid;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", ""};
        }
        Optional<String> url = setting("URL");
        if (url.isPresent()) {
            return new String[] {url.get(), setting("USERNAME").orElse("root"), setting("PASSWORD").orElse("root")};
        }
        JdbcDatabaseContainer<?> started = Containers.started(this, container);
        return new String[] {started.getJdbcUrl(), started.getUsername(), started.getPassword()};
    }

    private HikariDataSource open() {
        String[] settings = connectionSettings();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings[0]);
        config.setUsername(settings[1]);
        config.setPassword(settings[2]);
        config.setMaximumPoolSize(16);
        config.setPoolName("cosid-test-" + name().toLowerCase(Locale.ROOT));
        return new HikariDataSource(config);
    }

    /**
     * A new store instance on the shared data.
     */
    JdbcDialect dialect() {
        return dialect;
    }

        JdbcStore store() {
        return new JdbcStore(dataSource(), dialect, JdbcTables.DEFAULT, TIMEOUT);
    }

    /** The containers, started once per JVM; Testcontainers' Ryuk removes them when the JVM exits. */
    private static final class Containers {
        private static final java.util.Map<TestDatabase, JdbcDatabaseContainer<?>> STARTED = new java.util.EnumMap<>(TestDatabase.class);

        static synchronized JdbcDatabaseContainer<?> started(TestDatabase database, Supplier<JdbcDatabaseContainer<?>> container) {
            return STARTED.computeIfAbsent(database, d -> {
                JdbcDatabaseContainer<?> created = container.get();
                created.start();
                return created;
            });
        }
    }
}
