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

package me.ahoo.cosid.store.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * A data source without a pool, for a store the provider opens from {@code options.url} alone: each operation opens
 * a connection of its own. Applications that care about latency pass a pooled {@code DataSource} as the client.
 *
 * <p>Its login timeout and log writer are its own (R1-jdbc-09): setting them changes nothing of {@link DriverManager},
 * which is JVM-wide; a connection then opens under {@code DriverManager}'s login timeout. Its {@link #toString()} and the
 * failures of {@link #getConnection()} keep the password of the url out.
 */
final class DriverManagerDataSource implements DataSource {
    private final String url;
    private final String username;
    private final String password;
    private volatile int loginTimeout;
    private volatile PrintWriter logWriter;

    DriverManagerDataSource(String url, String username, String password) {
        this.url = Objects.requireNonNull(url, "url");
        this.username = username;
        this.password = password;
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            return username == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            throw withoutPassword(e);
        }
    }

    @Override
    public Connection getConnection(String user, String pass) throws SQLException {
        try {
            return DriverManager.getConnection(url, user, pass);
        } catch (SQLException e) {
            throw withoutPassword(e);
        }
    }

    /**
     * A failure as it is, unless its message quotes the url with a password, as {@code DriverManager}'s
     * {@code "No suitable driver found for <url>"} does (R2-mig-05): then a copy that quotes {@link #redacted} instead.
     */
    private SQLException withoutPassword(SQLException e) {
        String message = e.getMessage();
        String safe = redacted(url);
        if (message == null || safe.equals(url) || !message.contains(url)) {
            return e;
        }
        SQLException copy = new SQLException(message.replace(url, safe), e.getSQLState(), e.getErrorCode());
        copy.setStackTrace(e.getStackTrace());
        return copy;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new SQLException("Not a wrapper of " + type);
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(this);
    }

    @Override
    public String toString() {
        return "DriverManagerDataSource[" + redacted(url) + "]";
    }

    /** The url without a password: of its user info ({@code //user:secret@}) or of a {@code password} / {@code pwd} parameter. */
    static String redacted(String url) {
        return url.replaceAll("(//[^/:@?;]*):[^/@?;]*@", "$1:***@")
            .replaceAll("(?i)([?&;](password|pwd)=)[^&;]*", "$1***");
    }
}
