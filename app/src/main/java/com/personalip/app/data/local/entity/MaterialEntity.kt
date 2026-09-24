package com.personalip.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 素材元数据。真实文件始终在用户选择的本地文件夹中。
 *
 * [filePath] 存储相对根目录 PersonalIP 的相对路径（如 "素材库/男生大体重/1697_abc.jpg"），
 * 以便重新选择同一根文件夹后可重新解析（绝对 content:// Uri 会随 tree 变化）。
 */
@Entity(
    tableName = "materials",
    indices = [Index("categoryId"), Index(value = ["filePath"], unique = true)]
)
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 相对 PersonalIP 根的路径。 */
    val filePath: String,
    val categoryId: Long,
    /** 逗号/列表形式的标签，经 [com.personalip.app.data.local.Converters] 序列化。 */
    val tags: List<String> = emptyList(),
    val mimeType: String,
    /** OCR 识别出的文字（图片/截图才有，其它为空）。 */
    val ocrText: String? = null,
    val useCount: Int = 0,
    val lastUsedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
