package com.personalip.app.data.local

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Room 类型转换器：
 * - List<String> ↔ JSON 字符串（用于 tags、forbiddenWords 等列表字段）；
 * - [PostStatus] ↔ 字符串（Room 不自动转换枚举）。
 *
 * 仅序列化 List<String>，Moshi 内置即可处理，无需 kotlin-reflect。
 */
class Converters {

    private val moshi = Moshi.Builder().build()
    private val listStringType =
        Types.newParameterizedType(List::class.java, String::class.java)
    private val listAdapter get() = moshi.adapter<List<String>>(listStringType)

    @TypeConverter
    fun stringListToJson(value: List<String>?): String =
        if (value.isNullOrEmpty()) "[]" else listAdapter.toJson(value)

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else runCatching { listAdapter.fromJson(value) }.getOrNull() ?: emptyList()

    @TypeConverter
    fun postStatusToString(value: PostStatus): String = value.name

    @TypeConverter
    fun stringToPostStatus(value: String?): PostStatus =
        runCatching { PostStatus.valueOf(value ?: PostStatus.DRAFT.name) }
            .getOrDefault(PostStatus.DRAFT)
}
