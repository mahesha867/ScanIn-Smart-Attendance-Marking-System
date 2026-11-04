package com.attendance.util;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.TimeZone;

/**
 * Thread-Safe Database Connection Pool Manager.
 * Demonstrates:
 * - Singleton Design Pattern for connection pool lifecycle management
 * - Connection Pooling using Apache Tomcat JDBC Pool (DataSource)
 * - Encapsulation of JDBC configuration, validation queries, and connection limits
 */
public class DBConnection {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/attendance_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "root";

    private static DBConnection instance;
    private final DataSource dataSource;

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private static String resolveDbUrl() {
        String envUrl = System.getenv("DB_URL");
        if (envUrl == null || envUrl.trim().isEmpty()) {
            envUrl = System.getenv("DATABASE_URL");
        }
        if (envUrl == null || envUrl.trim().isEmpty()) {
            envUrl = System.getenv("MYSQL_URL");
        }
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            String url = envUrl.trim();
            if (url.startsWith("mysql://")) {
                url = "jdbc:" + url;
            }
            if (!url.contains("serverTimezone")) {
                String sep = url.contains("?") ? "&" : "?";
                url += sep + "serverTimezone=UTC&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
            }
            return url;
        }
        return DEFAULT_URL;
    }

    private static String resolveDbUser() {
        String user = System.getenv("DB_USER");
        if (user == null || user.trim().isEmpty()) user = System.getenv("DATABASE_USER");
        if (user == null || user.trim().isEmpty()) user = System.getenv("MYSQL_USER");
        return (user != null && !user.trim().isEmpty()) ? user.trim() : DEFAULT_USER;
    }

    private static String resolveDbPassword() {
        String pass = System.getenv("DB_PASSWORD");
        if (pass == null || pass.trim().isEmpty()) pass = System.getenv("DATABASE_PASSWORD");
        if (pass == null || pass.trim().isEmpty()) pass = System.getenv("MYSQL_PASSWORD");
        return (pass != null) ? pass : DEFAULT_PASSWORD;
    }

    private DBConnection() {
        PoolProperties p = new PoolProperties();
        p.setUrl(resolveDbUrl());
        p.setDriverClassName("com.mysql.cj.jdbc.Driver");
        p.setUsername(resolveDbUser());
        p.setPassword(resolveDbPassword());
        p.setInitSQL("SET time_zone = '+00:00'");
        p.setJmxEnabled(false);
        p.setTestWhileIdle(true);
        p.setTestOnBorrow(true);
        p.setValidationQuery("SELECT 1");
        p.setTestOnReturn(false);
        p.setValidationInterval(30000);
        p.setTimeBetweenEvictionRunsMillis(30000);
        p.setMaxActive(20);
        p.setInitialSize(5);
        p.setMaxWait(10000);
        p.setRemoveAbandonedTimeout(60);
        p.setMinEvictableIdleTimeMillis(30000);
        p.setMinIdle(5);
        p.setLogAbandoned(false);
        p.setRemoveAbandoned(true);

        this.dataSource = new DataSource();
        this.dataSource.setPoolProperties(p);
    }

    /**
     * Returns the singleton instance of DBConnection.
     */
    public static synchronized DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    /**
     * Returns the underlying pooled javax.sql.DataSource.
     */
    public javax.sql.DataSource getDataSource() {
        return this.dataSource;
    }

    /**
     * Borrows an active Connection from the connection pool.
     */
    public Connection getConnection() throws SQLException {
        return this.dataSource.getConnection();
    }

    /**
     * Closes the connection pool and releases all resources.
     */
    public void close() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }
}
