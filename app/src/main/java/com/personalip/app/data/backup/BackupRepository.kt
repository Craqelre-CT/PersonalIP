package com.personalip.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.personalip.app.data.storage.FileNameUtil
import com.personalip.app.data.storage.FolderConstants
import com.personalip.app.data.storage.RootFolderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 备份导出：将根目录 PersonalIP 下的所有文件打包为 Zip，
 * 默认保存到「备份/backup_YYYYMMDD_HHMMSS.zip」（排除 备份 目录自身，避免递归）。
 *
 * 也支持导出到用户通过 SAF 选择的外部位置（[exportTo]）。
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootFolderRepository: RootFolderRepository
) {
    /**
     * 导出到 备份/ 目录下的 zip。
     * @return 生成的 zip 文件 Uri；失败返回 null。
     */
    suspend fun exportToBackupDir(): Uri? = withContext(Dispatchers.IO) {
        val root = rootFolderRepository.getRootFolder() ?: return@withContext null
        val backupDir = rootFolderRepository.getBackupDir() ?: return@withContext null
        val fileName = "backup_${FileNameUtil.timestamp()}.zip"
        val zipFile = backupDir.createFile("application/zip", fileName) ?: return@withContext null
        val cr = context.contentResolver
        cr.openOutputStream(zipFile.uri)?.use { out ->
            zipTree(root, out, excludeName = FolderConstants.DIR_BACKUP)
        }
        zipFile.uri
    }

    /**
     * 导出到用户通过 SAF 选择的外部位置。
     * @param targetUri 由 ACTION_CREATE_DOCUMENT 返回的 Uri
     */
    suspend fun exportTo(targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val root = rootFolderRepository.getRootFolder() ?: return@withContext false
        val cr = context.contentResolver
        runCatching {
            cr.openOutputStream(targetUri)?.use { out ->
                zipTree(root, out, excludeName = FolderConstants.DIR_BACKUP)
            } != null
        }.getOrDefault(false)
    }

    /**
     * 遍历 DocumentFile 树并写入 ZipOutputStream。
     * @param excludeName 跳过的子目录名（如 备份 自身）
     */
    private fun zipTree(root: DocumentFile, out: OutputStream, excludeName: String? = null) {
        ZipOutputStream(out).use { zos ->
            val cr = context.contentResolver
            fun walk(file: DocumentFile, prefix: String) {
                if (file.isDirectory) {
                    val name = file.name ?: return
                    // 跳过备份目录自身，避免递归打包。
                    if (excludeName != null && prefix.endsWith("/$excludeName")) return
                    val childPrefix = if (prefix.isEmpty()) name else "$prefix/$name"
                    file.listFiles().forEach { walk(it, childPrefix) }
                } else {
                    val entryName = if (prefix.isEmpty()) (file.name ?: "file") else "$prefix/${file.name}"
                    runCatching {
                        zos.putNextEntry(ZipEntry(entryName))
                        cr.openInputStream(file.uri)?.use { input ->
                            val buf = ByteArray(8 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                zos.write(buf, 0, n)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
            // 根目录下的内容直接打包，不带根文件夹名前缀。
            root.listFiles().forEach { walk(it, "") }
        }
    }
}
