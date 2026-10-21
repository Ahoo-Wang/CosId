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

import lombok.SneakyThrows;
import org.h2.jdbcx.JdbcDataSource;

import java.util.UUID;
import javax.sql.DataSource;

/**
 * A fresh in-memory H2 database with the CosId tables of {@link JdbcDialect#H2}.
 */
final class RealH2 {
    private RealH2() {
    }
    
    static DataSource createDataSource() {
        return createDataSource("");
    }
    
    /**
     * @param mode extra URL settings, such as {@code ;MODE=MySQL}
     */
    @SneakyThrows
    static DataSource createDataSource(String mode) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:cosid_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1" + mode);
        return DialectTables.create(dataSource, JdbcDialect.H2);
    }
}
