package com.personalip.app.data.local

import com.personalip.app.data.local.dao.CategoryDao
import com.personalip.app.data.local.dao.PersonaDao
import com.personalip.app.data.local.entity.CategoryEntity
import com.personalip.app.data.local.entity.PersonaEntity
import com.personalip.app.data.storage.FolderConstants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首次启动时向数据库写入默认分类与人设占位行。
 * 幂等：仅在表为空时写入。
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val categoryDao: CategoryDao,
    private val personaDao: PersonaDao
) {
    suspend fun seedIfNeeded() {
        if (categoryDao.count() == 0) {
            val defaults = FolderConstants.DEFAULT_CATEGORIES.map { (displayName, folderName) ->
                CategoryEntity(displayName = displayName, folderName = folderName)
            }
            categoryDao.insertAll(defaults)
        }
        if (personaDao.get() == null) {
            personaDao.upsert(PersonaEntity()) // 空人设，用户后续填写
        }
    }
}
