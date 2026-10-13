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

import java.lang.reflect.Proxy;
import java.sql.Connection;
import javax.sql.DataSource;

/**
 * Hands out one connection every time, never closing it: a pool that keeps giving back the same session.
 */
final class SingleConnectionDataSource {
    private SingleConnectionDataSource() {
    }
    
    static DataSource of(Connection connection) {
        Connection unclosable = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
            (proxy, method, args) -> "close".equals(method.getName()) ? null : method.invoke(connection, args));
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    return unclosable;
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }
}
