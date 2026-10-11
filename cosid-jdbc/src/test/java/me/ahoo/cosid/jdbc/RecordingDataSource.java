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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Wraps a real {@link DataSource} and records what the code under test does with it: the SQL it prepares (or executes
 * without preparing),
 * the query timeouts it sets and the rollbacks. It can also make the next executions of statements starting with a
 * given SQL prefix fail, to reach error paths a real database rarely takes.
 */
final class RecordingDataSource implements DataSource {
    private final DataSource delegate;
    private final List<String> executedSql = new CopyOnWriteArrayList<>();
    private final List<Integer> queryTimeouts = new CopyOnWriteArrayList<>();
    private final AtomicInteger rollbacks = new AtomicInteger();
    private final AtomicInteger failingExecutions = new AtomicInteger();
    private final AtomicInteger attempts = new AtomicInteger();
    private volatile String failingSqlPrefix;
    private volatile SQLException failure;
    private volatile String interceptedSqlPrefix;
    private volatile SqlAction interception;
    
    /**
     * An action on the real database.
     */
    @FunctionalInterface
    interface SqlAction {
        void run() throws SQLException;
    }
    
    RecordingDataSource(DataSource delegate) {
        this.delegate = delegate;
    }
    
    /**
     * Make the next {@code times} executions of statements whose SQL starts with {@code sqlPrefix} throw a copy of
     * {@code sqlState}/{@code vendorCode}, and count every execution of them.
     */
    void failExecutions(String sqlPrefix, String sqlState, int vendorCode, int times) {
        this.failure = new SQLException("injected failure", sqlState, vendorCode);
        this.failingSqlPrefix = sqlPrefix;
        this.failingExecutions.set(times);
        this.attempts.set(0);
    }
    
    /**
     * Run {@code action} once, right before the next execution of a statement whose SQL starts with {@code sqlPrefix}:
     * another instance changing a row between the code's read and its conditional write.
     */
    void beforeNextExecution(String sqlPrefix, SqlAction action) {
        this.interception = action;
        this.interceptedSqlPrefix = sqlPrefix;
    }
    
    List<String> getExecutedSql() {
        return executedSql;
    }
    
    List<Integer> getQueryTimeouts() {
        return queryTimeouts;
    }
    
    int getRollbackCount() {
        return rollbacks.get();
    }
    
    /**
     * Executions of statements matching the prefix passed to {@link #failExecutions}, failed or not.
     */
    int getAttempts() {
        return attempts.get();
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return wrap(delegate.getConnection());
    }
    
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(delegate.getConnection(username, password));
    }
    
    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException invocationTargetException) {
            throw invocationTargetException.getCause();
        }
    }
    
    private Connection wrap(Connection connection) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "rollback":
                    rollbacks.incrementAndGet();
                    return invoke(connection, method, args);
                case "prepareStatement":
                    executedSql.add((String) args[0]);
                    PreparedStatement statement = (PreparedStatement) invoke(connection, method, args);
                    return wrap(statement, (String) args[0], PreparedStatement.class);
                case "createStatement":
                    return wrap((Statement) invoke(connection, method, args), null, Statement.class);
                default:
                    return invoke(connection, method, args);
            }
        };
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }
    
    private <T extends Statement> T wrap(T statement, String preparedSql, Class<T> type) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("setQueryTimeout".equals(name)) {
                queryTimeouts.add((Integer) args[0]);
            } else if (name.startsWith("execute")) {
                String sql = preparedSql;
                if (sql == null) {
                    sql = (String) args[0];
                    executedSql.add(sql);
                }
                String intercepted = interceptedSqlPrefix;
                if (intercepted != null && sql.startsWith(intercepted)) {
                    SqlAction action = interception;
                    interceptedSqlPrefix = null;
                    interception = null;
                    action.run();
                }
                String prefix = failingSqlPrefix;
                if (prefix != null && sql.startsWith(prefix)) {
                    attempts.incrementAndGet();
                    if (failingExecutions.getAndUpdate(left -> left > 0 ? left - 1 : 0) > 0) {
                        SQLException injected = failure;
                        throw new SQLException(injected.getMessage(), injected.getSQLState(), injected.getErrorCode());
                    }
                }
            }
            return invoke(statement, method, args);
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
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
