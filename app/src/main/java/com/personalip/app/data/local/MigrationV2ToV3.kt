package com.personalip.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3：排期从「周」改为「具体日期」。
 *
 * - weekly_plans 新增 date 列（NOT NULL DEFAULT ''）。旧排期的 date 为空，
 *   不会再出现在「今日待发 / 排期」视图中，等于自然退役，需重新生成计划。
 * - 分类设置里的 cooldownDays 统一移除，改为「跟随排期页的全局冷却期」。
 *   使用 SQLite 内置 JSON1 的 json_remove 精确删除该字段，保留其余设置。
 */
val MigrationV2ToV3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE weekly_plans ADD COLUMN date TEXT NOT NULL DEFAULT ''")
        runCatching {
            db.execSQL("UPDATE categories SET settingsJson = json_remove(settingsJson, '$.cooldownDays')")
        }
    }
}
