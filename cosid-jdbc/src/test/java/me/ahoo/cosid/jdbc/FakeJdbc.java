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

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A database without a database: records the statements, the auto-commit changes, commits and query timeouts.
 */
final class FakeJdbc implements DataSource {
    final List<String> executed = new ArrayList<>();
    final List<String> events = new ArrayList<>();
    final List<Integer> queryTimeouts = new ArrayList<>();
    boolean autoCommit;
    long maxId = 42;
    SQLException failure;
    String failingSqlPrefix = "";
    boolean generatedKeys = true;
    /**
     * The generated key, when it differs from what a select returns ({@link #maxId}).
     */
    Long generatedKey;
    
    FakeJdbc(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }
    
    @Override
    public Connection getConnection() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getAutoCommit":
                    return autoCommit;
                case "setAutoCommit":
                    autoCommit = (Boolean) args[0];
                    events.add("autoCommit=" + autoCommit);
                    return null;
                case "commit":
                case "rollback":
                    events.add(method.getName());
                    return null;
                case "prepareStatement":
                    return statement((String) args[0]);
                case "close":
                    events.add("close:autoCommit=" + autoCommit);
                    return null;
                case "isClosed":
                    return false;
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });
    }
    
    private PreparedStatement statement(String sql) {
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "setLong":
                case "setString":
                case "close":
                    return null;
                case "setQueryTimeout":
                    queryTimeouts.add((Integer) args[0]);
                    return null;
                case "executeUpdate":
                    execute(sql);
                    return 1;
                case "executeQuery":
                    execute(sql);
                    return resultSet(true, maxId);
                case "getGeneratedKeys":
                    return resultSet(generatedKeys, generatedKey != null ? generatedKey : maxId);
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });
    }
    
    private void execute(String sql) throws SQLException {
        executed.add(sql);
        if (failure != null && !failingSqlPrefix.isEmpty() && sql.startsWith(failingSqlPrefix)) {
            throw failure;
        }
    }
    
    private ResultSet resultSet(boolean hasRow, long value) {
        boolean[] read = {!hasRow};
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ResultSet.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "next":
                    boolean next = !read[0];
                    read[0] = true;
                    return next;
                case "getLong":
                    return value;
                case "close":
                    return null;
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });
    }
    
    @Override
    public Connection getConnection(String username, String password) {
        return getConnection();
    }
    
    @Override
    public PrintWriter getLogWriter() {
        return null;
    }
    
    @Override
    public void setLogWriter(PrintWriter out) {
    }
    
    @Override
    public void setLoginTimeout(int seconds) {
    }
    
    @Override
    public int getLoginTimeout() {
        return 0;
    }
    
    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }
    
    @Override
    public <T> T unwrap(Class<T> type) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public boolean isWrapperFor(Class<?> type) {
        return false;
    }
}
