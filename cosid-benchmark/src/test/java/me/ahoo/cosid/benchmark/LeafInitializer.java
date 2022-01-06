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

package me.ahoo.cosid.benchmark;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author ahoo wang
 */
public final class LeafInitializer {
    public static void initSegment(DataSource dataSource, String bizTag, long step) {
        String initIdSegmentSql = "insert into leaf_alloc(biz_tag, max_id, step, description)\n" +
                "values (?, 1, ?, 'LeafBenchmark')";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement initStatement = connection.prepareStatement(initIdSegmentSql)) {
                initStatement.setString(1, bizTag);
                initStatement.setLong(2, step);
                int affected = initStatement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
