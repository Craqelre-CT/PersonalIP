package com.personalip.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.personalip.app.data.local.dao.CategoryDao
import com.personalip.app.data.local.dao.MaterialDao
import com.personalip.app.data.local.dao.PersonaDao
import com.personalip.app.data.local.dao.PostDao
import com.personalip.app.data.local.dao.SettingDao
import com.personalip.app.data.local.dao.UsageRecordDao
import com.personalip.app.data.local.dao.WeeklyPlanDao
import com.personalip.app.data.local.entity.CategoryEntity
import com.personalip.app.data.local.entity.MaterialEntity
import com.personalip.app.data.local.entity.PersonaEntity
import com.personalip.app.data.local.entity.PostEntity
import com.personalip.app.data.local.entity.SettingEntity
import com.personalip.app.data.local.entity.UsageRecordEntity
import com.personalip.app.data.local.entity.WeeklyPlanEntity

/**
 * 应用唯一 Room 数据库。
 * 仅存元数据（路径、分类、标签、使用次数等）；真实文件始终在用户选择的 SAF 根目录。
 */
@Database(
    entities = [
        CategoryEntity::class,
        MaterialEntity::class,
        UsageRecordEntity::class,
        WeeklyPlanEntity::class,
        PostEntity::class,
        PersonaEntity::class,
        SettingEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PersonalIpDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun materialDao(): MaterialDao
    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun weeklyPlanDao(): WeeklyPlanDao
    abstract fun postDao(): PostDao
    abstract fun personaDao(): PersonaDao
    abstract fun settingDao(): SettingDao

    companion object {
        const val NAME = "personalip.db"
    }
}
