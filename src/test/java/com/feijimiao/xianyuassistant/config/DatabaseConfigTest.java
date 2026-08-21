package com.feijimiao.xianyuassistant.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
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
