package com.feijimiao.xianyuassistant.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void initializesReliableSqliteSettingsAndRecoveryTables() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        Path database = tempDir.resolve("reliability.db");
        ReflectionTestUtils.setField(config, "databaseUrl", "jdbc:sqlite:" + database);

        DataSource dataSource = config.dataSource();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals("wal", queryString(statement, "PRAGMA journal_mode"));
            assertEquals(10_000, queryInt(statement, "PRAGMA busy_timeout"));
            assertEquals(1, queryInt(statement,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='xianyu_auto_reply_delay_task'"));
            assertEquals(1, queryInt(statement,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='xianyu_delivery_lease'"));
        } finally {
            ((HikariDataSource) dataSource).close();
        }
    }

    @Test
    void upgradesLegacyOrderTableBeforeCreatingReviewRequestIndex() throws Exception {
        Path database = tempDir.resolve("legacy.db");
        String databaseUrl = "jdbc:sqlite:" + database;
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE xianyu_goods_order (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        xianyu_account_id BIGINT NOT NULL,
                        xy_goods_id VARCHAR(100) NOT NULL,
                        pnm_id VARCHAR(100) NOT NULL,
                        state TINYINT DEFAULT 0
                    )
                    """);
        }

        DatabaseConfig config = new DatabaseConfig();
        ReflectionTestUtils.setField(config, "databaseUrl", databaseUrl);

        DataSource dataSource = config.dataSource();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(1, queryInt(statement,
                    "SELECT COUNT(*) FROM pragma_table_info('xianyu_goods_order') " +
                            "WHERE name = 'review_request_count'"));
            assertEquals(1, queryInt(statement,
                    "SELECT COUNT(*) FROM pragma_table_info('xianyu_goods_order') " +
                            "WHERE name = 'last_review_request_at'"));
            assertEquals(1, queryInt(statement,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='index' " +
                            "AND name='idx_goods_order_review_request'"));
        } finally {
            ((HikariDataSource) dataSource).close();
        }
    }

    private String queryString(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1).toLowerCase();
        }
    }

    private int queryInt(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
