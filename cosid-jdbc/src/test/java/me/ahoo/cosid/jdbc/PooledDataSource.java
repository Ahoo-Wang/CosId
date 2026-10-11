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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * A small connection pool over a test {@link DataSource}, as in production: opening a connection per statement is slow
 * enough on a real database to push a distribution past the specs' safe guard window.
 */
final class PooledDataSource {
    
    private PooledDataSource() {
    }
    
    static DataSource of(DataSource dataSource, boolean autoCommit) {
        HikariConfig config = new HikariConfig();
        config.setDataSource(dataSource);
        config.setAutoCommit(autoCommit);
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }
}
