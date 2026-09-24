package com.personalip.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personalip.app.data.local.entity.CategoryEntity

/**
 * v1 → v2：categories 表新增 settingsJson 列。
 * 旧数据全部填默认值（autoOcr+autoTags 开、语气亲和、目标点赞、冷却7天）。
 */
val MigrationV1ToV2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE categories ADD COLUMN settingsJson TEXT NOT NULL DEFAULT '${CategoryEntity.DEFAULT_SETTINGS_JSON}'"
        )
    }
}
