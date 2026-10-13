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

package me.ahoo.cosid.spring.boot.starter.jdbc;

import me.ahoo.cosid.jdbc.JdbcDialect;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.util.Objects;
import java.util.function.Function;
import javax.sql.DataSource;

/**
 * Picks the SQL of the database behind a {@link DataSource}: a statement property left at its default (the MySQL
 * statement) takes the {@link JdbcDialect} statement of the database product, and any other value is kept.
 *
 * <p>The product is read from one connection, at the first statement that needs it. When it cannot be read, or the
 * database is not one of {@link JdbcDialect}, the MySQL statements stay, with a WARN.</p>
 *
 * @author ahoo wang
 * @since 4.1.0
 */
@Slf4j
public final class JdbcDialectDetector {
    private final DataSource dataSource;
    private boolean detected;
    private @Nullable JdbcDialect dialect;
    private @Nullable String product;
    
    public JdbcDialectDetector(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }
    
    public DataSource getDataSource() {
        return dataSource;
    }
    
    /**
     * The configured statement, or the dialect's when it is the MySQL default.
     *
     * @param configured   the property value
     * @param mysqlDefault the property's default
     * @param ofDialect    the statement of a dialect
     */
    public String sql(String configured, String mysqlDefault, Function<JdbcDialect, String> ofDialect) {
        if (!Objects.equals(configured, mysqlDefault)) {
            return configured;
        }
        JdbcDialect detectedDialect = dialect();
        return detectedDialect == null ? configured : ofDialect.apply(detectedDialect);
    }
    
    /**
     * The dialect of the database, or {@code null} when it cannot be read or is not supported.
     */
    public synchronized @Nullable JdbcDialect dialect() {
        if (!detected) {
            detected = true;
            detect();
        }
        return dialect;
    }
    
    /**
     * The database product name, or {@code null} when it could not be read.
     */
    public synchronized @Nullable String product() {
        dialect();
        return product;
    }
    
    private void detect() {
        try (Connection connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName();
        } catch (Exception exception) {
            if (log.isWarnEnabled()) {
                log.warn("Can not read the database product to choose the CosId SQL: the MySQL statements are used unless configured.", exception);
            }
            return;
        }
        dialect = JdbcDialect.of(product).orElse(null);
        if (dialect == null) {
            if (log.isWarnEnabled()) {
                log.warn("Database [{}] is not supported by CosId (MySQL, MariaDB, PostgreSQL, H2, SQL Server): the MySQL statements are used "
                    + "unless configured.", product);
            }
        } else if (log.isInfoEnabled()) {
            log.info("CosId uses the [{}] SQL for database [{}] where the statements are not configured.", dialect, product);
        }
    }
}
