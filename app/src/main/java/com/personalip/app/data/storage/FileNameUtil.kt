package com.personalip.app.data.storage

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 文件名工具：生成「时间戳 + 短 UUID」文件名，避免重名；
 * 并过滤掉文件名非法字符（/ \ : * ? " < > |）。
 */
object FileNameUtil {

    private val FORBIDDEN = FolderConstants.FORBIDDEN_FILE_CHARS.toSet()

    /** 过滤文件名非法字符。 */
    fun sanitize(input: String): String =
        input.filter { it !in FORBIDDEN }.trim()

    /** 当前时间戳字符串，格式 yyyyMMdd_HHmmss，用于备份文件名等。 */
    fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /**
     * 生成新文件名：[prefix]_yyyyMMddHHmmss_xxxxxxxx.[ext]
     * prefix 为空时省略前缀。ext 不含点号，可为空。
     */
    fun generate(prefix: String = "", ext: String): String {
        val ts = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        val uuid = UUID.randomUUID().toString().replace("-", "").take(8)
        val safeExt = sanitize(ext).lowercase()
        val name = buildString {
            if (prefix.isNotBlank()) append("${sanitize(prefix)}_")
            append(ts)
            append('_')
            append(uuid)
        }
        return if (safeExt.isBlank()) name else "$name.$safeExt"
    }

    /**
     * 推断扩展名：优先从源文件显示名取，其次按 MIME 映射，最后回退 jpg/默认。
     */
    fun extensionFor(context: Context, sourceUri: Uri, mimeType: String?): String {
        val fromName = queryDisplayName(context, sourceUri)
            ?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        if (!fromName.isNullOrBlank() && !fromName.contains('/')) return fromName
        val mime = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
        if (mime != null) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            if (!ext.isNullOrBlank()) return ext.lowercase()
        }
        return when (mime?.substringBefore('/')) {
            "image" -> "jpg"
            "video" -> "mp4"
            "text" -> "txt"
            else -> "bin"
        }
    }

    /** 查询 SAF/content Uri 的显示名。 */
    fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
