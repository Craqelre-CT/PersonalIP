package com.personalip.app.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalip.app.data.backup.BackupRepository
import com.personalip.app.data.local.dao.CategoryDao
import com.personalip.app.data.local.dao.SettingDao
import com.personalip.app.data.local.entity.CategoryEntity
import com.personalip.app.data.local.entity.PersonaEntity
import com.personalip.app.data.persona.PersonaRepository
import com.personalip.app.data.storage.RootFolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel：人设、新建分类、冷却期、备份导出、重新选择根文件夹。
 * AI 接口配置（baseUrl/模型/API Key）由 [com.personalip.app.ui.ai.AiConfigViewModel] 复用。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val personaRepository: PersonaRepository,
    private val categoryDao: CategoryDao,
    private val settingDao: SettingDao,
    private val backupRepository: BackupRepository,
    private val rootFolderRepository: RootFolderRepository
) : ViewModel() {

    /** 当前人设（可编辑）。 */
    private val _persona = MutableStateFlow(PersonaEntity())
    val persona: StateFlow<PersonaEntity> = _persona.asStateFlow()

    /** 冷却期（天）。 */
    val cooldownDays: StateFlow<Int> = settingDao.observeValue(KEY_COOLDOWN_DAYS)
        .map { it?.toIntOrNull() ?: 7 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 7)

    /** 当前根目录 Uri 字符串（用于显示）。 */
    val rootTreeUri: StateFlow<String?> = rootFolderRepository.rootTreeUriFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            _persona.value = personaRepository.get() ?: PersonaEntity()
        }
    }

    // ----- 人设 -----

    fun updatePersona(field: PersonaField, value: String) {
        val current = _persona.value
        _persona.value = when (field) {
            PersonaField.NICKNAME -> current.copy(nickname = value)
            PersonaField.IDENTITY -> current.copy(identity = value)
            PersonaField.BACKGROUND -> current.copy(background = value)
            PersonaField.PERSONALITY -> current.copy(personality = value)
            PersonaField.CATCHPHRASE -> current.copy(catchphrase = value)
            PersonaField.TARGET_AUDIENCE -> current.copy(targetAudience = value)
        }
    }

    fun updateForbiddenWords(raw: String) {
        val words = raw.split("\n", "，", ",").map { it.trim() }.filter { it.isNotBlank() }
        _persona.value = _persona.value.copy(forbiddenWords = words)
    }

    fun savePersona() {
        viewModelScope.launch {
            val ok = personaRepository.save(_persona.value)
            _message.value = if (ok) "人设已保存并同步到 人设/persona.json" else "人设已保存（JSON 落盘失败，可稍后重试）"
        }
    }

    // ----- 新建分类 -----

    fun createCategory(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                _message.value = "分类名不能为空"
                return@launch
            }
            // 同名检查
            if (categoryDao.getByFolderName(trimmed) != null) {
                _message.value = "分类「$trimmed」已存在"
                return@launch
            }
            // 在 素材库/ 下创建子文件夹
            val materialsDir = rootFolderRepository.getMaterialsDir()
            if (materialsDir == null) {
                _message.value = "根目录不可用，请先授权"
                return@launch
            }
            val existing = materialsDir.findFile(trimmed)
            if (existing == null && materialsDir.createDirectory(trimmed) == null) {
                _message.value = "创建分类文件夹失败"
                return@launch
            }
            categoryDao.upsert(
                CategoryEntity(displayName = trimmed, folderName = trimmed)
            )
            _message.value = "已新建分类「$trimmed」"
        }
    }

    // ----- 冷却期 -----

    fun setCooldown(days: Int) {
        viewModelScope.launch { settingDao.upsert(com.personalip.app.data.local.entity.SettingEntity(KEY_COOLDOWN_DAYS, days.toString())) }
    }

    // ----- 备份导出 -----

    fun exportBackup() {
        viewModelScope.launch {
            _busy.value = true
            val uri = runCatching { backupRepository.exportToBackupDir() }.getOrNull()
            _busy.value = false
            _message.value = uri?.let { "已导出备份：${it.lastPathSegment ?: "备份目录"}" }
                ?: "备份导出失败，请检查根目录权限"
        }
    }

    // ----- 重新选择根文件夹 -----

    fun reselectRoot(treeUri: Uri, migrate: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            val ok = runCatching {
                rootFolderRepository.reselectRoot(treeUri, migrate) {}
            }.getOrDefault(false)
            _busy.value = false
            _message.value = if (ok) "已切换根文件夹" + if (migrate) "（已迁移旧数据）" else ""
            else "切换根文件夹失败，请重试"
        }
    }

    fun clearMessage() { _message.value = null }

    companion object {
        const val KEY_COOLDOWN_DAYS = "cooldown_days"
    }
}

/** 人设可编辑字段。 */
enum class PersonaField {
    NICKNAME, IDENTITY, BACKGROUND, PERSONALITY, CATCHPHRASE, TARGET_AUDIENCE
}
