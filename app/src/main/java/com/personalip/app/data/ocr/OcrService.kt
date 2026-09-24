package com.personalip.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 聊天截图 OCR：使用 ML Kit 中文识别器提取文字，方便 AI 理解聊天截图素材。
 *
 * 失败或无文字时返回空字符串，不抛异常（OCR 非关键路径）。
 */
@Singleton
class OcrService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /** 是否为可 OCR 的图片类型。 */
    fun isImage(mimeType: String?): Boolean =
        mimeType != null && mimeType.startsWith("image/")

    /**
     * 识别图片中的文字。会先把图片解码为下采样 Bitmap，避免大图 OOM。
     */
    suspend fun recognizeText(uri: Uri, maxDim: Int = 1600): String = withContext(Dispatchers.IO) {
        val bitmap = decodeSampledBitmap(uri, maxDim) ?: return@withContext ""
        try {
            recognizeFromBitmap(bitmap)
        } catch (_: Throwable) {
            ""
        }
    }

    private suspend fun recognizeFromBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result.text)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resume("")
                }
        }

    /** 解码为下采样 Bitmap，限制最长边为 [maxDim]。 */
    private fun decodeSampledBitmap(uri: Uri, maxDim: Int): Bitmap? = runCatching {
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
