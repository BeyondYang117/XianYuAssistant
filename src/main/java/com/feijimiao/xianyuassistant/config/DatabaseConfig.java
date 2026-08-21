package com.feijimiao.xianyuassistant.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import javax.sql.DataSource;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 数据库配置类
 */
@Slf4j
@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url:jdbc:sqlite:xianyu_assistant.db}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        log.info("初始化SQLite数据库...");

        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqliteConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        sqliteConfig.setBusyTimeout(10_000);

        SQLiteDataSource sqliteDataSource = new SQLiteDataSource(sqliteConfig);
        sqliteDataSource.setUrl(databaseUrl);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("sqlite-pool");
        hikariConfig.setDataSource(sqliteDataSource);
        hikariConfig.setMaximumPoolSize(4);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(10_000);
        hikariConfig.setValidationTimeout(3_000);
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        
        // 打印数据库文件路径
        String dbPath = databaseUrl.replace("jdbc:sqlite:", "");
        File dbFile = new File(dbPath);
        try {
            log.info("数据库文件路径: {}", dbFile.getCanonicalPath());
        } catch (Exception e) {
            log.warn("获取数据库文件绝对路径失败: {}", e.getMessage());
            log.info("数据库文件路径(相对路径): {}", dbPath);
        }
        
        // 初始化数据库表结构
        initDatabase(dataSource);
        
        log.info("SQLite数据库初始化完成");
        return dataSource;
    }

    /**
     * 初始化数据库表结构
     */
    private void initDatabase(DataSource dataSource) {
        try {
            // 检查数据库文件是否存在
            String dbPath = databaseUrl.replace("jdbc:sqlite:", "");
            File dbFile = new File(dbPath);
            
            // 确保数据库目录存在
            File dbDir = dbFile.getParentFile();
            if (dbDir != null && !dbDir.exists()) {
                boolean created = dbDir.mkdirs();
                if (created) {
                    log.info("创建数据库目录: {}", dbDir.getAbsolutePath());
                } else {
                    log.warn("数据库目录创建失败: {}", dbDir.getAbsolutePath());
                }
            }
            
            boolean isNewDatabase = !dbFile.exists();
            
            if (isNewDatabase) {
                log.info("数据库文件不存在，将创建新数据库: {}", dbFile.getAbsolutePath());
            } else {
                log.info("使用现有数据库文件: {}", dbFile.getAbsolutePath());
            }

            // 读取SQL脚本
            ClassPathResource resource = new ClassPathResource("sql/schema.sql");
            String sql = FileCopyUtils.copyToString(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );

            // 执行SQL脚本。旧数据库必须先补字段，再创建依赖新字段的索引。
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                // 智能分割：识别触发器(CREATE TRIGGER...BEGIN...END;)作为完整语句
                List<String> statements = splitSqlStatements(sql);
                int executedCount = 0;

                // CREATE TABLE IF NOT EXISTS 只负责新库，现有表不会自动增加新字段。
                // 因此先确保所有表存在，再根据最新表定义迁移字段。
                for (String sqlStatement : statements) {
                    String cleanSql = removeComments(sqlStatement.trim());

                    if (isCreateTableStatement(cleanSql)) {
                        executeSql(stmt, cleanSql);
                        executedCount++;
                    }
                }

                migrateMissingColumns(stmt);

                for (String sqlStatement : statements) {
                    String cleanSql = removeComments(sqlStatement.trim());

                    if (!cleanSql.isEmpty() && !isCreateTableStatement(cleanSql)) {
                        executeSql(stmt, cleanSql);
                        executedCount++;
                    }
                }
                
                if (isNewDatabase) {
                    log.info("数据库表结构创建成功，执行了 {} 条SQL语句", executedCount);
                } else {
                    log.info("数据库表结构检查完成，执行了 {} 条SQL语句", executedCount);
                }
            }

            // 验证表是否创建成功
            verifyTables(dataSource);

        } catch (Exception e) {
            log.error("初始化数据库失败", e);
            throw new RuntimeException("初始化数据库失败: " + e.getMessage(), e);
        }
    }

    private boolean isCreateTableStatement(String sql) {
        return !sql.isEmpty() && sql.toUpperCase(Locale.ROOT).startsWith("CREATE TABLE");
    }

    private void executeSql(Statement stmt, String sql) throws Exception {
        try {
            stmt.execute(sql);
            log.debug("执行SQL成功: {}", sql.substring(0, Math.min(50, sql.length())));
        } catch (Exception e) {
            log.error("执行SQL失败: {}", sql, e);
            throw e;
        }
    }

    /**
     * 按最新 schema 为现有表补齐字段，确保后续索引和触发器可以安全创建。
     */
    private void migrateMissingColumns(Statement stmt) throws Exception {
        SqlSchemaParser.SchemaDefinition schema = new SqlSchemaParser().parseSchemaFile("sql/schema.sql");
        int addedCount = 0;

        for (SqlSchemaParser.TableDefinition table : schema.getTables().values()) {
            Set<String> existingColumns = getTableColumns(stmt, table.getName());

            for (SqlSchemaParser.ColumnDefinition column : table.getColumns()) {
                if (existingColumns.contains(column.getName().toLowerCase(Locale.ROOT))) {
                    continue;
                }

                String alterSql = "ALTER TABLE " + quoteIdentifier(table.getName())
                        + " ADD COLUMN " + column.getDefinition();
                log.info("为现有表添加缺失字段: {}.{}", table.getName(), column.getName());
                executeSql(stmt, alterSql);
                existingColumns.add(column.getName().toLowerCase(Locale.ROOT));
                addedCount++;
            }
        }

        if (addedCount > 0) {
            log.info("数据库字段迁移完成，共添加 {} 个字段", addedCount);
        }
    }

    private Set<String> getTableColumns(Statement stmt, String tableName) throws Exception {
        Set<String> columns = new HashSet<>();
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + quoteIdentifier(tableName) + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * 智能分割SQL语句
     * 识别CREATE TRIGGER...BEGIN...END;作为完整语句，其他按;分割
     */
    private List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inTrigger = false;
        
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            
            // 跳过注释行
            if (trimmed.startsWith("--")) {
                continue;
            }
            
            // 检测触发器开始
            if (trimmed.toUpperCase().startsWith("CREATE TRIGGER")) {
                inTrigger = true;
                current = new StringBuilder();
            }
            
            current.append(line).append("\n");
            
            if (inTrigger) {
                // 触发器以END;结束
                if (trimmed.toUpperCase().equals("END;") || trimmed.equals("END;")) {
                    statements.add(current.toString().trim());
                    current = new StringBuilder();
                    inTrigger = false;
                }
            } else {
                // 普通语句以;结束
                if (trimmed.endsWith(";")) {
                    statements.add(current.toString().trim());
                    current = new StringBuilder();
                }
            }
        }
        
        // 处理最后可能剩余的内容
        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            statements.add(remaining);
        }
        
        return statements;
    }

    /**
     * 移除SQL注释
     */
    private String removeComments(String sql) {
        if (sql == null || sql.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmedLine = line.trim();
            // 跳过注释行和空行
            if (!trimmedLine.startsWith("--") && !trimmedLine.isEmpty()) {
                result.append(line).append("\n");
            }
        }
        return result.toString().trim();
    }
    
    /**
     * 验证表是否创建成功
     */
    private void verifyTables(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // 查询表列表
            var rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
            );
            
            log.info("数据库表列表:");
            while (rs.next()) {
                String tableName = rs.getString("name");
                log.info("  - {}", tableName);
            }
            
        } catch (Exception e) {
            log.error("验证数据库表失败", e);
        }
    }
}
