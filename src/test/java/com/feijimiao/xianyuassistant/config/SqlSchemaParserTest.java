package com.feijimiao.xianyuassistant.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * schema.sql 由启动时的 DatabaseInitListener 用正则解析后自动建表，
 * 解析失败只会被记录成日志而不会让启动失败，所以新增 DDL 必须有一个能跑的校验。
 */
class SqlSchemaParserTest {

    private static boolean hasColumn(SqlSchemaParser.TableDefinition table, String column) {
        List<SqlSchemaParser.ColumnDefinition> columns = table.getColumns();
        return columns != null && columns.stream().anyMatch(c -> column.equals(c.getName()));
    }

    @Test
    void parsesAccountTaskTables() {
        SqlSchemaParser.SchemaDefinition schema = new SqlSchemaParser().parseSchemaFile("sql/schema.sql");

        SqlSchemaParser.TableDefinition setting = schema.getTables().get("xianyu_account_task_setting");
        assertNotNull(setting, "未解析到 xianyu_account_task_setting 表");
        assertTrue(hasColumn(setting, "auto_polish_on"));
        assertTrue(hasColumn(setting, "polish_time"));
        assertTrue(hasColumn(setting, "last_polish_date"));
        assertTrue(hasColumn(setting, "last_polish_at"));
        assertTrue(hasColumn(setting, "auto_rate_on"));
        assertTrue(hasColumn(setting, "rate_content"));
        assertTrue(hasColumn(setting, "last_rate_scan_at"));

        SqlSchemaParser.TableDefinition run = schema.getTables().get("xianyu_account_task_run");
        assertNotNull(run, "未解析到 xianyu_account_task_run 表");
        assertTrue(hasColumn(run, "run_key"));
        assertTrue(hasColumn(run, "task_type"));
        assertTrue(hasColumn(run, "status"));
    }

    @Test
    void accountTaskRunKeyIndexIsUnique() {
        SqlSchemaParser.SchemaDefinition schema = new SqlSchemaParser().parseSchemaFile("sql/schema.sql");

        SqlSchemaParser.IndexDefinition runKeyIndex = schema.getIndexes().get("idx_account_task_run_key");
        assertNotNull(runKeyIndex, "未解析到 run_key 索引");
        // 幂等抢占完全依赖这个唯一索引，退化成普通索引会导致同一商品当天被重复擦亮
        assertTrue(runKeyIndex.isUnique(), "run_key 索引必须是 UNIQUE");
        assertEquals("xianyu_account_task_run", runKeyIndex.getTableName());
    }
}
