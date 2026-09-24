package com.personalip.app.data.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 相册信息。 */
data class AlbumBucket(
    val id: Long,
    val name: String,
    val coverUri: Uri,
    val count: Int
)

/** 媒体项（图片或视频）。 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long,
    val mimeType: String?,
    val isVideo: Boolean,
    val bucketId: Long? = null
)

/** 查询系统相册与媒体的工具。 */
object MediaGallery {

    private val IMAGE_PROJECTION = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
    )

    private val VIDEO_PROJECTION = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.BUCKET_ID,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
    )

    suspend fun queryImageAlbums(context: Context): List<AlbumBucket> =
        queryAlbums(context, includeImages = true, includeVideos = false)

    suspend fun queryAllAlbums(context: Context): List<AlbumBucket> =
        queryAlbums(context, includeImages = true, includeVideos = true)

    private suspend fun queryAlbums(
        context: Context,
        includeImages: Boolean,
        includeVideos: Boolean,
    ): List<AlbumBucket> = withContext(Dispatchers.IO) {
        val bucketMedia = linkedMapOf<Long, MutableList<MediaItem>>()
        val bucketNames = mutableMapOf<Long, String>()

        if (includeImages) {
            queryMedia(
                context = context,
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection = IMAGE_PROJECTION,
                isVideo = false,
                outBucketNames = bucketNames,
            ).forEach { item ->
                val bid = item.bucketId ?: return@forEach
                bucketMedia.getOrPut(bid) { mutableListOf() }.add(item)
            }
        }

        if (includeVideos) {
            queryMedia(
                context = context,
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection = VIDEO_PROJECTION,
                isVideo = true,
                outBucketNames = bucketNames,
            ).forEach { item ->
                val bid = item.bucketId ?: return@forEach
                bucketMedia.getOrPut(bid) { mutableListOf() }.add(item)
            }
        }

        bucketMedia.entries.map { (bucketId, items) ->
            val sorted = items.sortedByDescending { it.dateAdded }
            val cover = sorted.firstOrNull()?.uri ?: Uri.EMPTY
            val name = bucketNames[bucketId]?.takeIf { it.isNotBlank() } ?: "未知相册"
            AlbumBucket(
                id = bucketId,
                name = name,
                coverUri = cover,
                count = items.size
            )
        }.sortedByDescending { it.count }
    }

    /**
     * 查询媒体列表，同时收集 bucketId -> 相册名映射。
     * 不使用 LIMIT 子句（部分 OEM ROM 会报 SQL 语法错误 "Invalid token LIMIT"）。
     */
    private fun queryMedia(
        context: Context,
        collection: Uri,
        projection: Array<String>,
        isVideo: Boolean,
        outBucketNames: MutableMap<Long, String>,
    ): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        try {
            context.contentResolver.query(
                collection, projection, null, null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val bucketIdx = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val bucketId = if (bucketIdx >= 0) cursor.getLong(bucketIdx) else null
                    // 收集 bucketName（取该桶第一个有效的）
                    if (bucketId != null && bucketNameIdx >= 0) {
                        val name = cursor.getString(bucketNameIdx)
                        if (!name.isNullOrBlank() && !outBucketNames.containsKey(bucketId)) {
                            outBucketNames[bucketId] = name
                        }
                    }
                    items += MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        dateAdded = cursor.getLong(dateIdx),
                        mimeType = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null,
                        isVideo = isVideo,
                        bucketId = bucketId,
                    )
                }
            }
        } catch (e: SecurityException) {
            // 没有存储权限时返回空列表，让上层提示用户
        }
        return items
    }

    /** 查询指定相册内的媒体（按添加时间倒序）。 */
    suspend fun queryMediaInAlbum(
        context: Context,
        bucketId: Long,
        limit: Int = 0,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MediaItem>()

        fun collectFrom(collection: Uri, projection: Array<String>, isVideo: Boolean) {
            try {
                context.contentResolver.query(
                    collection, projection,
                    "${MediaStore.MediaColumns.BUCKET_ID} = ?",
                    arrayOf(bucketId.toString()),
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    while (cursor.moveToNext()) {
                        results += MediaItem(
                            id = cursor.getLong(idIdx),
                            uri = ContentUris.withAppendedId(
                                collection,
                                cursor.getLong(idIdx)
                            ),
                            dateAdded = cursor.getLong(dateIdx),
                            mimeType = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null,
                            isVideo = isVideo,
                        )
                        if (limit > 0 && results.size >= limit) break
                    }
                }
            } catch (_: SecurityException) {
            }
        }

        collectFrom(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, IMAGE_PROJECTION, false)
        if (limit > 0 && results.size >= limit) return@withContext results
        collectFrom(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, VIDEO_PROJECTION, true)

        results
    }
}
