package com.personalip.app.data.material

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.personalip.app.data.local.dao.MaterialDao
import com.personalip.app.data.local.entity.MaterialEntity
import com.personalip.app.data.ocr.OcrService
import com.personalip.app.data.storage.FolderConstants
import com.personalip.app.data.storage.FileNameUtil
import com.personalip.app.data.storage.RootFolderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 素材库业务逻辑：导入（复制到 SAF 分类目录）、OCR、标签、搜索、删除。
 *
 * 真实文件始终在用户选择的 SAF 根目录下；本仓库只读写元数据 + 复制字节。
 * [filePath] 存「相对 PersonalIP 根的相对路径」，重选根后可重新解析。
 */
@Singleton
class MaterialRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootFolderRepository: RootFolderRepository,
    private val materialDao: MaterialDao,
    private val ocrService: OcrService
) {
    /** 观察某分类下的素材（categoryId = -1 表示全部）。 */
    fun observeFiltered(categoryId: Long, query: String): Flow<List<MaterialEntity>> =
        materialDao.observeFiltered(categoryId, query)

    suspend fun getById(id: Long): MaterialEntity? = materialDao.getById(id)

    /**
     * 导入一个素材：复制源文件到「素材库/<categoryFolderName>/」下，
     * 图片则做 OCR 提取文字，最后写入 materials 元数据。
     *
     * @return 新素材 id；失败返回 null。
     */
    suspend fun importMaterial(
        sourceUri: Uri,
        categoryFolderName: String,
        categoryId: Long,
        tags: List<String> = emptyList()
    ): Long? = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val mimeType = runCatching { cr.getType(sourceUri) }.getOrNull() ?: "application/octet-stream"

        // 1. 确保分类目录存在。
        val categoryDir = rootFolderRepository.getCategoryDir(categoryFolderName)
            ?: return@withContext null

        // 2. 生成文件名并创建文件。
        val ext = FileNameUtil.extensionFor(context, sourceUri, mimeType)
        val fileName = FileNameUtil.generate(prefix = categoryFolderName, ext = ext)
        val destFile = categoryDir.createFile(mimeType, fileName) ?: return@withContext null

        // 3. 复制字节。createFile 后真实名可能与传入不同（provider 规范化），用 destFile.name 落库。
        val copied = runCatching {
            cr.openInputStream(sourceUri)?.use { input ->
                cr.openOutputStream(destFile.uri)?.use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) {
            runCatching { destFile.delete() }
            return@withContext null
        }

        // 4. 相对路径与 OCR。
        val relativePath = listOf(
            FolderConstants.DIR_MATERIALS,
            categoryFolderName,
            destFile.name
        ).joinToString("/")

        val ocrText = if (ocrService.isImage(mimeType)) {
            runCatching { ocrService.recognizeText(destFile.uri) }.getOrNull()
        } else null

        // 5. 写元数据。
        val entity = MaterialEntity(
            filePath = relativePath,
            categoryId = categoryId,
            tags = tags,
            mimeType = mimeType,
            ocrText = ocrText
        )
        materialDao.insert(entity)
    }

    /** 更新标签。 */
    suspend fun updateTags(materialId: Long, tags: List<String>) = withContext(Dispatchers.IO) {
        val existing = materialDao.getById(materialId) ?: return@withContext
        materialDao.update(existing.copy(tags = tags))
    }

    /** 重新对图片素材执行 OCR（手动触发）。 */
    suspend fun reRunOcr(materialId: Long): String? = withContext(Dispatchers.IO) {
        val existing = materialDao.getById(materialId) ?: return@withContext null
        if (!ocrService.isImage(existing.mimeType)) return@withContext existing.ocrText
        val doc = resolveMaterialDocument(existing) ?: return@withContext existing.ocrText
        val text = runCatching { ocrService.recognizeText(doc.uri) }.getOrNull()
        if (text != existing.ocrText) {
            materialDao.update(existing.copy(ocrText = text))
        }
        text
    }

    /** 记录一次使用（仅更新计数与时间；usage_records 行在第 5 阶段排期写入）。 */
    suspend fun recordUsage(materialId: Long, weekId: String, postId: Long? = null) {
        materialDao.incrementUsage(materialId, System.currentTimeMillis())
    }

    /** 删除素材：同时删除真实文件与元数据。 */
    suspend fun deleteMaterial(materialId: Long): Boolean = withContext(Dispatchers.IO) {
        val existing = materialDao.getById(materialId) ?: return@withContext false
        val doc = resolveMaterialDocument(existing)
        runCatching { doc?.delete() }
        materialDao.deleteById(materialId)
        true
    }

    /** 解析素材真实文件为 DocumentFile（用于缩略图 / 重新 OCR）。 */
    suspend fun resolveMaterialDocument(material: MaterialEntity): DocumentFile? =
        withContext(Dispatchers.IO) {
            val root = rootFolderRepository.getRootFolder() ?: return@withContext null
            resolveRelative(root, material.filePath)
        }

    /** 解析素材真实文件的 Uri（Coil 可直接加载）。 */
    suspend fun resolveMaterialUri(material: MaterialEntity): Uri? =
        resolveMaterialDocument(material)?.uri

    /** 按相对路径从根逐级查找子节点。 */
    private fun resolveRelative(root: DocumentFile, relativePath: String): DocumentFile? {
        var current: DocumentFile = root
        for (segment in relativePath.split('/')) {
            if (segment.isBlank()) continue
            current = current.findFile(segment) ?: return null
        }
        return current
    }
}
