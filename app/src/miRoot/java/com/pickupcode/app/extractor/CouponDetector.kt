package com.pickupcode.app.extractor

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.pickupcode.app.aicr.AicrBarcodeClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** MI-root 专用：只调用系统 AICR 条码插件，结果仅接纳非空码值。 */
object CouponDetector {
    private const val TAG = "PickupAicrBarcode"
    private val mutex = Mutex()

    data class CouponResult(val rawValue: String?)

    suspend fun detect(bitmap: Bitmap, context: Context? = null): List<CouponResult> = mutex.withLock {
        val appContext = context?.applicationContext
            ?: throw IllegalArgumentException("MI-root barcode recognition requires an application context")
        val ownedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: return@withLock emptyList()
        try {
            val values = suspendCancellableCoroutine { continuation ->
                val request = AicrBarcodeClient.scan(appContext, ownedBitmap) { result, error ->
                    if (!ownedBitmap.isRecycled) ownedBitmap.recycle()
                    if (!continuation.isActive) return@scan
                    when {
                        error is CancellationException -> continuation.cancel(error)
                        error != null -> continuation.resumeWithException(error)
                        else -> continuation.resume(result.orEmpty())
                    }
                }
                continuation.invokeOnCancellation { request.cancel() }
            }
            values.asSequence().filter(String::isNotBlank).distinct().map(::CouponResult).toList()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "system barcode service unavailable; no bundled fallback (${error.javaClass.simpleName})")
            emptyList()
        } finally {
            if (!ownedBitmap.isRecycled) ownedBitmap.recycle()
        }
    }

    suspend fun close() {
        mutex.withLock { }
    }
}
