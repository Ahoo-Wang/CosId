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
import me.ahoo.cosid.jdbc.exception.NotFoundMaxIdException;
import me.ahoo.cosid.jdbc.exception.SegmentNameMissingException;
import me.ahoo.cosid.segment.IdSegmentDistributor;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Jdbc IdSegment Distributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class JdbcIdSegmentDistributor implements IdSegmentDistributor {

    public static final String INCREMENT_MAX_ID_SQL
        = "update cosid set last_max_id=(last_max_id + ?),last_fetch_time=unix_timestamp() where name = ?;";
    public static final String FETCH_MAX_ID_SQL
        = "select last_max_id from cosid where name = ?;";

    private final String namespace;
    private final String name;
    private final long step;
    private final DataSource dataSource;
    private final String incrementMaxIdSql;
    private final String fetchMaxIdSql;
    private final int queryTimeoutSeconds;
    private volatile long lastMaxId;

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
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name can not be empty!");
        Preconditions.checkArgument(step > 0, "step:[%s] must be greater than 0!", step);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(incrementMaxIdSql), "incrementMaxIdSql can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(fetchMaxIdSql), "fetchMaxIdSql can not be empty!");
        Preconditions.checkNotNull(dataSource, "dataSource can not be null!");

        this.namespace = namespace;
        this.name = name;
        this.step = step;
        this.incrementMaxIdSql = incrementMaxIdSql;
        this.fetchMaxIdSql = fetchMaxIdSql;
        this.dataSource = dataSource;
        this.queryTimeoutSeconds = JdbcSupport.toQueryTimeoutSeconds(queryTimeout);
    }

    @Override
    public @NonNull String getNamespace() {
        return namespace;
    }

    @Override
    public @NonNull String getName() {
        return name;
    }

    @Override
    public long getStep() {
        return step;
    }

    @Override
    public long nextMaxId(long step) {
        IdSegmentDistributor.ensureStep(step);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            final long nextMinMaxId = lastMaxId + step;
            long nextMaxId;
            try {
                nextMaxId = incrementAndFetch(connection, step);
                connection.commit();
            } catch (Throwable throwable) {
                // Never hand a connection with an open transaction (and its row lock) back to the pool.
                JdbcSupport.rollbackQuietly(connection, throwable);
                throw throwable;
            }
            // The stored max id went backwards (e.g. restored from a backup): this range overlaps ids already issued.
            Preconditions.checkState(nextMaxId >= nextMinMaxId, "nextMaxId:[%s] must be greater than nextMinMaxId:[%s]!", nextMaxId, nextMinMaxId);
            this.lastMaxId = nextMaxId;
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
