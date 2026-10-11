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

import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Hands out connections with auto-commit off, like a pool configured with {@code autoCommit=false}, optionally with a
 * schema (search path) set.
 */
final class ManualCommitDataSource implements DataSource {
    private final DataSource delegate;
    @Nullable
    private final String schema;
    
    ManualCommitDataSource(DataSource delegate, @Nullable String schema) {
        this.delegate = delegate;
        this.schema = schema;
    }
    
    ManualCommitDataSource(DataSource delegate) {
        this(delegate, null);
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = delegate.getConnection();
        if (schema != null) {
            connection.setSchema(schema);
        }
        connection.setAutoCommit(false);
        return connection;
    }
    
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }
    
    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }
    
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }
    
    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }
    
    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }
    
    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}
