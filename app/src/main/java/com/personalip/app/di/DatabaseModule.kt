package com.personalip.app.di

import android.content.Context
import androidx.room.Room
import com.personalip.app.data.local.PersonalIpDatabase
import com.personalip.app.data.local.dao.CategoryDao
import com.personalip.app.data.local.dao.MaterialDao
import com.personalip.app.data.local.dao.PersonaDao
import com.personalip.app.data.local.dao.PostDao
import com.personalip.app.data.local.dao.SettingDao
import com.personalip.app.data.local.dao.UsageRecordDao
import com.personalip.app.data.local.dao.WeeklyPlanDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PersonalIpDatabase =
        Room.databaseBuilder(
            context,
            PersonalIpDatabase::class.java,
            PersonalIpDatabase.NAME
        )
            // v1→v2：categories 表新增 settingsJson 列，默认值由 CategoryEntity.DEFAULT_SETTINGS_JSON 提供。
            .addMigrations(com.personalip.app.data.local.MigrationV1ToV2)
            // 兜底：未来若再加字段未及时写迁移，允许重建而非崩溃（开发期友好）。
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideCategoryDao(db: PersonalIpDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideMaterialDao(db: PersonalIpDatabase): MaterialDao = db.materialDao()
    @Provides fun provideUsageRecordDao(db: PersonalIpDatabase): UsageRecordDao = db.usageRecordDao()
    @Provides fun provideWeeklyPlanDao(db: PersonalIpDatabase): WeeklyPlanDao = db.weeklyPlanDao()
    @Provides fun providePostDao(db: PersonalIpDatabase): PostDao = db.postDao()
    @Provides fun providePersonaDao(db: PersonalIpDatabase): PersonaDao = db.personaDao()
    @Provides fun provideSettingDao(db: PersonalIpDatabase): SettingDao = db.settingDao()
}
