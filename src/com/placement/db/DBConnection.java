package com.placement.db;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {

    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;
    private static int serverPort = 8086;
    private static boolean initialized = false;

    private static synchronized void loadConfig() {
        if (initialized) return;

        Properties props = new Properties();
        File configFile = new File("config/db.properties");
        if (!configFile.exists()) {
            // Check current directory fallback
            configFile = new File("../config/db.properties");
        }

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                dbUrl = props.getProperty("db.url", "jdbc:oracle:thin:@localhost:1521:xe");
                dbUser = props.getProperty("db.user", "PLACEMENT_USER");
                dbPassword = props.getProperty("db.password", "placement123");
                serverPort = Integer.parseInt(props.getProperty("server.port", "8086"));
                String driver = props.getProperty("db.driver", "oracle.jdbc.OracleDriver");
                Class.forName(driver);
                initialized = true;
                System.out.println("[DB] Loaded configuration from " + configFile.getPath());
            } catch (Exception e) {
                System.err.println("[DB-ERROR] Failed to load config/db.properties: " + e.getMessage());
            }
        } else {
            // Fallback defaults
            dbUrl = "jdbc:oracle:thin:@localhost:1521:xe";
            dbUser = "PLACEMENT_USER";
            dbPassword = "placement123";
            serverPort = 8086;
            try {
                Class.forName("oracle.jdbc.OracleDriver");
                initialized = true;
                System.out.println("[DB] Using default Oracle configuration.");
            } catch (ClassNotFoundException e) {
                System.err.println("[DB-ERROR] Oracle JDBC Driver class not found: " + e.getMessage());
            }
        }
    }

    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            loadConfig();
        }
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    public static int getServerPort() {
        if (!initialized) {
            loadConfig();
        }
        return serverPort;
    }

    public static String getDbUrl() {
        if (!initialized) loadConfig();
        return dbUrl;
    }

    public static String getDbUser() {
        if (!initialized) loadConfig();
        return dbUser;
    }
}
