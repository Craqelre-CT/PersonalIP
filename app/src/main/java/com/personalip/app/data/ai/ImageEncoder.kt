package com.personalip.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 将图片素材编码为 base64 data URI，供支持多模态的模型理解图片内容。
 * 大图先下采样，避免请求体过大。
 */
@Singleton
class ImageEncoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun encodeToDataUri(uri: Uri, maxDim: Int = 1024, quality: Int = 80): String? =
        withContext(Dispatchers.IO) {
            val bitmap = decodeSampled(uri, maxDim) ?: return@withContext null
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        }

    private fun decodeSampled(uri: Uri, maxDim: Int): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxDim) {
                decoder.setTargetSampleSize((longest.toFloat() / maxDim).toInt().coerceAtLeast(1))
            }
            decoder.setMutableRequired(true)
        }
    }.getOrNull()
}
