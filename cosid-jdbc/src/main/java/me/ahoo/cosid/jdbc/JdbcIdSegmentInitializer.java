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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Jdbc IdSegment Initializer.
 *
 * @author ahoo wang
 */
@Slf4j
public class JdbcIdSegmentInitializer {
    
    public static final String INIT_COSID_TABLE_SQL =
        "create table if not exists cosid\n"
            + "(\n"
            + "    name            varchar(100) not null comment '{namespace}.{name}',\n"
            + "    last_max_id     bigint unsigned not null default 0,\n"
            + "    last_fetch_time bigint unsigned not null default 0,\n"
            + "    constraint cosid_pk\n"
            + "        primary key (name)\n"
            + ") engine = InnoDB;";
    public static final String INIT_ID_SEGMENT_SQL
        = "insert into cosid (name, last_max_id,last_fetch_time) value (?, ?,unix_timestamp());";
    
    private final String initCosIdTableSql;
    private final String initIdSegmentSql;
    private final DataSource dataSource;
    
    public JdbcIdSegmentInitializer(DataSource dataSource) {
        this(INIT_COSID_TABLE_SQL, INIT_ID_SEGMENT_SQL, dataSource);
    }
    
    public JdbcIdSegmentInitializer(String initCosIdTableSql, String initIdSegmentSql, DataSource dataSource) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(initCosIdTableSql), "initCosIdTableSql can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(initIdSegmentSql), "initIdSegmentSql can not be empty!");
        Preconditions.checkNotNull(dataSource, "dataSource can not be null!");
        
        this.initCosIdTableSql = initCosIdTableSql;
        this.initIdSegmentSql = initIdSegmentSql;
        this.dataSource = dataSource;
    }
    
    public int initCosIdTable() throws SQLException {
        if (log.isInfoEnabled()) {
            log.info("Init CosIdTable");
        }
        
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement initStatement = connection.prepareStatement(initCosIdTableSql)) {
                int affected = initStatement.executeUpdate();
                return affected;
            }
        }
    }
    
    public boolean tryInitCosIdTable() {
        try {
            initCosIdTable();
            return true;
        } catch (Throwable throwable) {
            if (log.isInfoEnabled()) {
                log.info("Try Init CosIdTable failed.[{}]", throwable.getMessage());
            }
            return false;
        }
    }
    
    public int initIdSegment(String segmentName, long offset) throws SQLException, SQLIntegrityConstraintViolationException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(segmentName), "segmentName can not be empty!");
        Preconditions.checkArgument(offset >= 0, "offset:[%s] must be greater than or equal to 0!", offset);
        
        if (log.isInfoEnabled()) {
            log.info("Init IdSegment - segmentName:[{}] - offset:[{}]", segmentName, offset);
        }
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement initStatement = connection.prepareStatement(initIdSegmentSql)) {
                initStatement.setString(1, segmentName);
                initStatement.setLong(2, offset);
                int affected = initStatement.executeUpdate();
                return affected;
            }
        }
    }
    
    public boolean tryInitIdSegment(String segmentName, long offset) {
        try {
            initIdSegment(segmentName, offset);
            return true;
        } catch (Throwable throwable) {
            if (log.isInfoEnabled()) {
                log.info("Try Init IdSegment failed.[{}]", throwable.getMessage());
            }
            return false;
        }
    }
}
