package com.pickupcode.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.pickupcode.app.aicr.AicrOcrClient
import com.pickupcode.app.aicr.AicrOcrResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** MI-root 专用：只调用系统 AICR OCR V2，不内置或回退到其它模型。 */
object OCREngine {
    private const val TAG = "PickupAicrOcr"
    private val mutex = Mutex()

    data class LineBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun width(): Int = right - left
        fun height(): Int = bottom - top
        fun centerY(): Int = (top + bottom) / 2
    }

    data class TextLine(val text: String, val boundingBox: LineBox?, val confidence: Float?)

    private val unicodeDashes = Regex("[\\u2010-\\u2015\\u2212\\uFE58\\uFE63\\uFF0D\\u30FC]")

    suspend fun recognize(bitmap: Bitmap, context: Context? = null): List<TextLine> = mutex.withLock {
        val appContext = context?.applicationContext
            ?: throw IllegalArgumentException("MI-root OCR requires an application context")
        val ownedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: return@withLock emptyList()
        try {
            val result = suspendCancellableCoroutine { continuation ->
                val request = AicrOcrClient.recognizeWithLines(appContext, ownedBitmap) { value, error ->
                    if (!ownedBitmap.isRecycled) ownedBitmap.recycle()
                    if (!continuation.isActive) return@recognizeWithLines
                    when {
                        error is CancellationException -> continuation.cancel(error)
                        error != null -> continuation.resumeWithException(error)
                        value == null -> continuation.resume(AicrOcrResult("", emptyList()))
                        else -> continuation.resume(value)
                    }
                }
                continuation.invokeOnCancellation { request.cancel() }
            }
            result.lines.mapNotNull { line ->
                val text = line.text?.trim().orEmpty().replace(unicodeDashes, "-")
                if (text.isEmpty()) null else TextLine(
                    text = text,
                    boundingBox = polygonBounds(line.polygon, bitmap.width, bitmap.height),
                    confidence = null
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 日志只保留失败阶段的异常类型，不包含识别文本或码值。
            Log.w(TAG, "system OCR unavailable; no bundled fallback (${error.javaClass.simpleName})")
            emptyList()
        } finally {
            if (!ownedBitmap.isRecycled) ownedBitmap.recycle()
        }
    }

    /** AICR V2 polygon 是相对传入图片的交错 x/y 点；异常或越界坐标不参与几何评分。 */
    internal fun polygonBounds(points: FloatArray?, imageWidth: Int, imageHeight: Int): LineBox? {
        if (points == null || points.size < 6 || points.size % 2 != 0) return null
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (index in points.indices step 2) {
            val x = points[index]
            val y = points[index + 1]
            if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f || x > imageWidth || y > imageHeight) return null
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
        }
        if (maxX <= minX || maxY <= minY) return null
        return LineBox(minX.toInt(), minY.toInt(), kotlin.math.ceil(maxX).toInt(), kotlin.math.ceil(maxY).toInt())
    }

    /** Wait for the active AICR request to finish; it owns its own Binder session and cleanup. */
    suspend fun close() {
        mutex.withLock { }
    }
}
