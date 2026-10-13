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

package me.ahoo.cosid.jdbc;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.jdbc.exception.NotFoundMaxIdException;
import me.ahoo.cosid.jdbc.exception.SegmentNameMissingException;
import me.ahoo.cosid.segment.AbstractIdSegmentDistributor;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;

/**
 * Jdbc IdSegment Distributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class JdbcIdSegmentDistributor extends AbstractIdSegmentDistributor {

    public static final String INCREMENT_MAX_ID_SQL
        = "update cosid set last_max_id=(last_max_id + ?),last_fetch_time=unix_timestamp() where name = ?;";
    public static final String FETCH_MAX_ID_SQL
        = "select last_max_id from cosid where name = ?;";

    private final DataSource dataSource;
    private final String incrementMaxIdSql;
    private final String fetchMaxIdSql;
    private final int queryTimeoutSeconds;

    public JdbcIdSegmentDistributor(String namespace, String name, long step, DataSource dataSource) {
        this(namespace, name, step, INCREMENT_MAX_ID_SQL, FETCH_MAX_ID_SQL, dataSource);
    }

    public JdbcIdSegmentDistributor(String namespace, String name, long step, String incrementMaxIdSql, String fetchMaxIdSql, DataSource dataSource) {
        this(namespace, name, step, incrementMaxIdSql, fetchMaxIdSql, dataSource, JdbcSupport.DEFAULT_QUERY_TIMEOUT);
    }

    /**
     * Create a JDBC segment distributor.
     *
     * @param queryTimeout timeout applied to each statement, {@code null} or zero means no timeout
     */
    public JdbcIdSegmentDistributor(String namespace, String name, long step, String incrementMaxIdSql, String fetchMaxIdSql, DataSource dataSource,
                                    Duration queryTimeout) {
        super(namespace, name, step);
        Checks.checkArgument(!Checks.isNullOrEmpty(incrementMaxIdSql), "incrementMaxIdSql can not be empty!");
        Checks.checkArgument(!Checks.isNullOrEmpty(fetchMaxIdSql), "fetchMaxIdSql can not be empty!");
        Checks.checkNotNull(dataSource, "dataSource can not be null!");

        this.incrementMaxIdSql = incrementMaxIdSql;
        this.fetchMaxIdSql = fetchMaxIdSql;
        this.dataSource = dataSource;
        this.queryTimeoutSeconds = JdbcSupport.toQueryTimeoutSeconds(queryTimeout);
    }




    @Override
    protected long doNextMaxId(long step) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            long nextMaxId;
            try {
                nextMaxId = incrementAndFetch(connection, step);
                connection.commit();
            } catch (Throwable throwable) {
                // Never hand a connection with an open transaction (and its row lock) back to the pool.
                JdbcSupport.rollbackQuietly(connection, throwable);
                throw throwable;
            }
            return nextMaxId;
        } catch (SQLException sqlException) {
            if (log.isErrorEnabled()) {
                log.error(sqlException.getMessage(), sqlException);
            }
            throw new CosIdException(sqlException.getMessage(), sqlException);
        }
    }

    private long incrementAndFetch(Connection connection, long step) throws SQLException {
        try (PreparedStatement accStatement = JdbcSupport.prepareStatement(connection, incrementMaxIdSql, queryTimeoutSeconds)) {
            accStatement.setLong(1, step);
            accStatement.setString(2, getNamespacedName());
            int affected = accStatement.executeUpdate();
            if (affected == 0) {
                throw new SegmentNameMissingException(getNamespacedName());
            }
        }

        try (PreparedStatement fetchStatement = JdbcSupport.prepareStatement(connection, fetchMaxIdSql, queryTimeoutSeconds)) {
            fetchStatement.setString(1, getNamespacedName());
            try (ResultSet resultSet = fetchStatement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new NotFoundMaxIdException(getNamespacedName());
                }
                return resultSet.getLong(1);
            }
        }
    }

}
