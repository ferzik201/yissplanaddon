package ru.bambolumba.yissplanaddon.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ru.bambolumba.yissplanaddon.config.PluginConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final Logger logger;

    public DatabaseManager(PluginConfig config, Logger logger) {
        this.logger = logger;

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:mariadb://" + config.dbHost() + ":" + config.dbPort() + "/" + config.dbName());
        hikari.setUsername(config.dbUser());
        hikari.setPassword(config.dbPassword());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setPoolName("YissPlanAddon-Pool");
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "25");

        this.dataSource = new HikariDataSource(hikari);
    }

    public void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ypa_economy_snapshots (
                      id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                      captured_at DATETIME NOT NULL,
                      total_coins DOUBLE   NOT NULL,
                      top5_json   TEXT     NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ypa_economy_income (
                      id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                      captured_at  DATETIME NOT NULL,
                      income_coeff DOUBLE   NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка создания таблиц БД", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
