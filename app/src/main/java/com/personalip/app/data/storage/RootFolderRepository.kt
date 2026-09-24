package com.personalip.app.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.personalip.app.data.settings.AppDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 根目录 SAF 授权与文件夹结构管理。
 *
 * 关键设计：
 * - 不申请 MANAGE_EXTERNAL_STORAGE，全程走 Storage Access Framework。
 * - 首次让用户选择一个文件夹作为存储位置，App 在该位置下创建根文件夹 PersonalIP。
 * - 通过 [Context.takePersistableUriPermission] 持久化授权，重启后仍可读写。
 * - 所有文件读写通过 DocumentFile API；数据库只存元数据。
 * - 卸载 App 后素材不丢；重新安装再次选择同一根文件夹即可恢复。
 */
@Singleton
class RootFolderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: AppDataStore
) {
    /** 当前持久化的 SAF tree Uri。 */
    val rootTreeUriFlow: Flow<String?> = dataStore.rootTreeUriFlow.flowOn(Dispatchers.IO)

    /**
     * 是否已授权：Uri 已保存且持久化权限仍有效且根目录可访问。
     * 同时返回该 Uri，便于 UI 直接使用。
     *
     * 说明：权限「外部撤销」需重启后才会被检测到（DataStore 在启动期重新读取
     * 存储的 Uri，再校验当前 persistedUriPermissions），运行期内的外部撤销不实时感知，
     * 属可接受行为。
     */
    val isAuthorizedFlow: Flow<AuthorizationState> = dataStore.rootTreeUriFlow.map { uriStr ->
        val uri = uriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri == null || !isUriAccessible(uri)) {
            AuthorizationState.Unauthorized
        } else {
            AuthorizationState.Authorized(uri)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 处理用户选择文件夹后的结果：
     * 1. 取得持久化读写权限；
     * 2. 在所选文件夹下创建 PersonalIP 及全部子目录；
     * 3. 持久化 Uri 字符串。
     *
     * 返回 true 表示成功；返回 false 表示权限或结构创建失败。
     */
    suspend fun authorizeAndCreateStructure(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        // 1. 持久化权限。OpenDocumentTree 返回的 Uri 是可持久化的。
        runCatching {
            cr.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure { return@withContext false }

        // 2. 创建根文件夹与子结构（幂等）。
        if (!ensureStructureInternal(treeUri)) {
            return@withContext false
        }

        // 3. 持久化 Uri。
        dataStore.setRootTreeUri(treeUri.toString())
        true
    }

    /**
     * 幂等地补齐根目录结构（用于升级或缺失子目录时调用）。
     */
    suspend fun ensureStructure(): Boolean = withContext(Dispatchers.IO) {
        val uri = currentTreeUri() ?: return@withContext false
        ensureStructureInternal(uri)
    }

    /** 返回根文件夹（PersonalIP），未授权或不存在返回 null。 */
    suspend fun getRootFolder(): DocumentFile? = withContext(Dispatchers.IO) {
        val uri = currentTreeUri() ?: return@withContext null
        if (!isUriAccessible(uri)) return@withContext null
        val tree = DocumentFile.fromTreeUri(context, uri) ?: return@withContext null
        tree.findFile(FolderConstants.ROOT_FOLDER_NAME)?.takeIf { it.isDirectory }
    }

    /** 返回「素材库」目录。 */
    suspend fun getMaterialsDir(): DocumentFile? =
        getChildDir(FolderConstants.DIR_MATERIALS)

    /** 返回「输出」目录。 */
    suspend fun getOutputDir(): DocumentFile? =
        getChildDir(FolderConstants.DIR_OUTPUT)

    /** 返回「人设」目录。 */
    suspend fun getPersonaDir(): DocumentFile? =
        getChildDir(FolderConstants.DIR_PERSONA)

    /** 返回「配置」目录。 */
    suspend fun getConfigDir(): DocumentFile? =
        getChildDir(FolderConstants.DIR_CONFIG)

    /** 返回「备份」目录。 */
    suspend fun getBackupDir(): DocumentFile? =
        getChildDir(FolderConstants.DIR_BACKUP)

    /** 返回某分类对应的素材子目录（按 folderName 查找）。 */
    suspend fun getCategoryDir(folderName: String): DocumentFile? = withContext(Dispatchers.IO) {
        val materials = getChildDir(FolderConstants.DIR_MATERIALS) ?: return@withContext null
        materials.findFile(folderName)?.takeIf { it.isDirectory }
    }

    /**
     * 重新选择根文件夹。
     * @param newTreeUri 新选择文件夹的 tree Uri
     * @param migrate 是否将旧 PersonalIP 内容迁移到新位置
     * @param onProgress 迁移过程中回调已复制的文件数
     */
    suspend fun reselectRoot(
        newTreeUri: Uri,
        migrate: Boolean,
        onProgress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        // 1. 取得新权限。
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                newTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure { return@withContext false }

        // 2. 在新位置创建结构。
        if (!ensureStructureInternal(newTreeUri)) return@withContext false

        // 3. 迁移旧数据（若需要且旧根存在）。
        val oldTreeUri = currentTreeUri()
        if (migrate && oldTreeUri != null && isUriAccessible(oldTreeUri)) {
            val oldTree = DocumentFile.fromTreeUri(context, oldTreeUri)
            val oldRoot = oldTree?.findFile(FolderConstants.ROOT_FOLDER_NAME)
            val newTree = DocumentFile.fromTreeUri(context, newTreeUri)
            val newRoot = newTree?.findFile(FolderConstants.ROOT_FOLDER_NAME)
            if (oldRoot != null && newRoot != null) {
                var copied = 0
                copyTree(oldRoot, newRoot) { copied++; onProgress(copied) }
            }
        }

        // 4. 释放旧权限并持久化新 Uri。
        if (oldTreeUri != null && oldTreeUri != newTreeUri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    oldTreeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        dataStore.setRootTreeUri(newTreeUri.toString())
        true
    }

    /**
     * 清除当前授权（不会删除用户文件）。
     * 用于「重新选择」前的清理或异常恢复。
     */
    suspend fun clearAuthorization() = withContext(Dispatchers.IO) {
        val uri = currentTreeUri()
        if (uri != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        dataStore.setRootTreeUri(null)
    }

    // ----- 内部实现 -----

    private suspend fun currentTreeUri(): Uri? =
        rootTreeUriFlow.first()?.let { runCatching { Uri.parse(it) }.getOrNull() }

    private fun isUriAccessible(uri: Uri): Boolean {
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        if (!granted) return false
        return runCatching {
            DocumentFile.fromTreeUri(context, uri)?.exists() == true
        }.getOrDefault(false)
    }

    /** 幂等地在所选 tree 下创建 PersonalIP 及全部子目录。 */
    private fun ensureStructureInternal(treeUri: Uri): Boolean {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val root = findOrCreateDir(tree, FolderConstants.ROOT_FOLDER_NAME) ?: return false
        FolderConstants.TOP_LEVEL_DIRS.forEach { name ->
            findOrCreateDir(root, name) ?: return false
        }
        val materials = root.findFile(FolderConstants.DIR_MATERIALS) ?: return false
        FolderConstants.DEFAULT_CATEGORIES.forEach { (_, folderName) ->
            findOrCreateDir(materials, folderName) ?: return false
        }
        return true
    }

    /** 查找或创建子目录，名称禁止使用非法字符。 */
    private fun findOrCreateDir(parent: DocumentFile, name: String): DocumentFile? {
        val safe = sanitizeFolderName(name)
        if (safe.isBlank()) return null
        parent.findFile(safe)?.takeIf { it.isDirectory() }?.let { return it }
        return parent.createDirectory(safe)
    }

    private fun sanitizeFolderName(name: String): String =
        name.filter { it !in FolderConstants.FORBIDDEN_FILE_CHARS }.trim()

    private suspend fun getChildDir(name: String): DocumentFile? = withContext(Dispatchers.IO) {
        val root = getRootFolder() ?: return@withContext null
        root.findFile(name)?.takeIf { it.isDirectory }
    }

    /** 递归复制整棵子树（文件 + 子目录）。 */
    private fun copyTree(
        source: DocumentFile,
        destParent: DocumentFile,
        onFileCopied: () -> Unit
    ) {
        source.listFiles().forEach { child ->
            if (child.isDirectory) {
                val newDir = destParent.createDirectory(child.name ?: return@forEach)
                    ?: return@forEach
                copyTree(child, newDir, onFileCopied)
            } else {
                copyFile(child, destParent, child.name ?: "file")
                onFileCopied()
            }
        }
    }

    private fun copyFile(source: DocumentFile, destParent: DocumentFile, destName: String) {
        val target = destParent.createFile(source.type ?: "application/octet-stream", destName)
            ?: return
        runCatching {
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                context.contentResolver.openOutputStream(target.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}

/** 根目录授权状态。 */
sealed interface AuthorizationState {
    /** 未授权（未选择 / 权限被撤销 / 根目录丢失）。 */
    data object Unauthorized : AuthorizationState
    /** 已授权，携带根 tree Uri。 */
    data class Authorized(val treeUri: Uri) : AuthorizationState
}
