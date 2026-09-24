package com.personalip.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalip.app.data.local.CategorySettingsMapper
import com.personalip.app.data.local.dao.CategoryDao
import com.personalip.app.data.local.dao.MaterialDao
import com.personalip.app.data.local.dao.UsageRecordDao
import com.personalip.app.data.local.dao.WeeklyPlanDao
import com.personalip.app.data.local.entity.CategoryEntity
import com.personalip.app.data.local.entity.MaterialEntity
import com.personalip.app.data.material.MaterialRepository
import com.personalip.app.domain.model.CategorySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val materialRepository: MaterialRepository,
    private val categoryDao: CategoryDao,
    private val materialDao: MaterialDao,
    private val usageRecordDao: UsageRecordDao,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val settingsMapper: CategorySettingsMapper
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** 当前进入的文件夹 ID，null 表示在文件夹列表页。 */
    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    /** 当前文件夹实体（如果已进入）。 */
    val currentFolder: StateFlow<CategoryEntity?> =
        _currentFolderId.flatMapLatest { id -> categories.map { list -> list.firstOrNull { it.id == id } } }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // 文件夹列表页搜索关键字
    private val _folderQuery = MutableStateFlow("")
    val folderQuery: StateFlow<String> = _folderQuery.asStateFlow()

    /** 文件夹列表过滤后的结果（按搜索关键字）。 */
    val filteredCategories: StateFlow<List<CategoryEntity>> =
        combine(categories, _folderQuery.debounce(200)) { list, q ->
            if (q.isBlank()) list
            else list.filter { it.displayName.contains(q, ignoreCase = true) }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 文件夹内素材页的查询
    private val _materialQuery = MutableStateFlow("")
    val materialQuery: StateFlow<String> = _materialQuery.asStateFlow()

    private val _tagFilter = MutableStateFlow<List<String>>(emptyList())
    val tagFilter: StateFlow<List<String>> = _tagFilter.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // 当前文件夹内素材（带 tag 过滤）
    private val folderMaterials = _currentFolderId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
        else materialRepository.observeFiltered(id, _materialQuery.value)
    }

    val allTags: StateFlow<List<String>> = folderMaterials
        .map { list -> list.flatMap { it.tags }.distinct().sorted() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val items: StateFlow<List<MaterialDisplayItem>> =
        combine(folderMaterials, categories, _tagFilter) { materials, cats, tags ->
            val byId = cats.associateBy { it.id }
            val tagFiltered = if (tags.isEmpty()) materials
            else materials.filter { it.tags.containsAll(tags) }
            tagFiltered.map { entity ->
                MaterialDisplayItem(
                    entity = entity,
                    categoryDisplay = byId[entity.categoryId]?.displayName ?: "未分类",
                    uri = materialRepository.resolveMaterialUri(entity),
                    isImage = entity.mimeType.startsWith("image/")
                )
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ===== 导航 =====
    fun enterFolder(id: Long) { _currentFolderId.value = id }
    fun backToFolders() { _currentFolderId.value = null }

    // ===== 搜索 =====
    fun setFolderQuery(q: String) { _folderQuery.value = q }
    fun setMaterialQuery(q: String) { _materialQuery.value = q }
    fun toggleTagFilter(tag: String) {
        _tagFilter.update { if (it.contains(tag)) it - tag else it + tag }
    }
    fun clearMessage() { _message.value = null }

    // ===== 文件夹管理 =====

    /** 新建文件夹。返回新建的 ID；失败返回 null（如重名）。 */
    suspend fun createFolder(name: String): Long? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@withContext null
        if (categoryDao.getByFolderName(trimmed) != null) {
            _message.value = "已存在同名文件夹"
            return@withContext null
        }
        val id = categoryDao.upsert(CategoryEntity(displayName = trimmed, folderName = trimmed))
        id
    }

    /** 重命名文件夹。displayName 与 folderName 同时更新（保持一致策略）。 */
    suspend fun renameFolder(id: Long, newName: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return@withContext false
        // 检查目标名是否被其他文件夹占用
        val existing = categoryDao.getByFolderName(trimmed)
        if (existing != null && existing.id != id) {
            _message.value = "已存在同名文件夹"
            return@withContext false
        }
        categoryDao.rename(id, trimmed, trimmed)
        true
    }

    /** 更新文件夹设置。 */
    suspend fun updateFolderSettings(id: Long, settings: CategorySettings) = withContext(Dispatchers.IO) {
        categoryDao.updateSettings(id, settingsMapper.encode(settings))
    }

    /** 读取文件夹设置（用于设置弹窗回显）。 */
    suspend fun getFolderSettings(id: Long): CategorySettings = withContext(Dispatchers.IO) {
        val cat = categoryDao.getById(id) ?: return@withContext CategorySettings.DEFAULT
        settingsMapper.decode(cat.settingsJson)
    }

    /** 删除文件夹及其下所有素材 + 关联排期/使用记录（级联清理）。 */
    suspend fun deleteFolder(id: Long): Boolean = withContext(Dispatchers.IO) {
        val cat = categoryDao.getById(id) ?: return@withContext false
        // 1. 拉取该分类下所有素材
        val materials = materialDao.getByCategory(id)
        // 2. 删除关联排期 + 使用记录（避免外键悬空）
        for (m in materials) {
            weeklyPlanDao.deleteByMaterial(m.id)
            usageRecordDao.deleteByMaterial(m.id)
            materialRepository.deleteMaterial(m.id)
        }
        // 3. 删除文件夹实体
        categoryDao.deleteById(id)
        if (_currentFolderId.value == id) _currentFolderId.value = null
        true
    }

    // ===== 素材导入 =====

    /** 从相册导入到指定文件夹（PickMultipleVisualMedia 回调）。 */
    fun importUris(uris: List<Uri>, targetCategoryId: Long? = null) {
        val catId = targetCategoryId ?: _currentFolderId.value
        if (catId == null) {
            _message.value = "请先选择目标文件夹"
            return
        }
        if (uris.isEmpty()) return
        val folderName = categories.value.firstOrNull { it.id == catId }?.folderName
        if (folderName == null) {
            _message.value = "文件夹信息缺失，请重试"
            return
        }
        _importing.value = true
        viewModelScope.launch {
            var ok = 0; var fail = 0
            for (uri in uris) {
                val id = runCatching {
                    materialRepository.importMaterial(uri, folderName, catId)
                }.getOrNull()
                if (id != null) ok++ else fail++
            }
            _importing.value = false
            _message.value = if (fail == 0) "已导入 $ok 个素材到「$folderName」"
            else "导入完成：成功 $ok，失败 $fail"
        }
    }

    // ===== 素材管理 =====

    fun updateTags(materialId: Long, tags: List<String>) {
        viewModelScope.launch {
            materialRepository.updateTags(materialId, tags)
            _message.value = "标签已更新"
        }
    }

    fun reRunOcr(materialId: Long) {
        viewModelScope.launch {
            materialRepository.reRunOcr(materialId)
            _message.value = "OCR 已更新"
        }
    }

    fun deleteMaterial(materialId: Long) {
        viewModelScope.launch {
            // 级联清理：关联排期 + 使用记录
            weeklyPlanDao.deleteByMaterial(materialId)
            usageRecordDao.deleteByMaterial(materialId)
            val ok = materialRepository.deleteMaterial(materialId)
            _message.value = if (ok) "已删除" else "删除失败"
        }
    }
}

data class MaterialDisplayItem(
    val entity: MaterialEntity,
    val categoryDisplay: String,
    val uri: Uri?,
    val isImage: Boolean
)
