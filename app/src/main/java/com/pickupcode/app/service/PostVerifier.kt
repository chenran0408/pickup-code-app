package com.pickupcode.app.service

import android.content.Context
import android.util.Log
import com.pickupcode.app.BuildConfig
import com.pickupcode.app.geocoder.GeocoderVerifier
import com.pickupcode.app.kuaidi100.Kuaidi100Verifier

/**
 * 识别后置验证（PostVerifier）：抽取无障碍服务（PickupCodeAccessibilityService）与
 * 分享接收（ShareReceiver）路径共用的「地图地址验证 / 快递100 反向验证」。
 *
 * 两处共用相同的验证调用与日志，差异仅在验证成功后的处理——用回调参数化
 * （地图）或返回结果由调用方决定回填策略（快递100）。
 */
object PostVerifier {

    private const val TAG = "PostVerifier"

    /** 地图地址验证。成功时回调 [onVerified]（confidence, formattedAddress）；返回是否验证通过。 */
    suspend fun verifyMap(
        context: Context,
        address: String,
        amapApiKey: String?,
        onVerified: suspend (confidence: Float, formattedAddress: String?) -> Unit
    ): Boolean {
        val result = GeocoderVerifier.verify(context, address, amapApiKey = amapApiKey)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Map verify: verified=${result.verified}, confidence=${result.confidence}, provider=${result.provider}, address=$address")
        }
        if (result.verified) {
            onVerified(result.confidence, result.formattedAddress)
        }
        return result.verified
    }

    /** 快递100 反向验证。成功且命中码值返回 [Kuaidi100Verifier.KuaidiResult]，否则返回 null。 */
    suspend fun verifyKuaidi100(
        context: Context,
        key: String,
        trackingNum: String,
        ocrCodes: List<String>
    ): Kuaidi100Verifier.KuaidiResult? {
        val res = Kuaidi100Verifier.query(key, trackingNum, Kuaidi100Verifier.guessCourierCode(trackingNum))
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Kuaidi100 verify: success=${res.success} code=${res.pickUpCode} address=${res.pickUpAddress} err=${res.errorMsg}")
            if (res.success && res.pickUpCode != null) {
                if (ocrCodes.contains(res.pickUpCode)) {
                    Log.d(TAG, "Kuaidi100 confirm: OCR码 ${res.pickUpCode} 与 API 一致 ✓")
                } else {
                    Log.d(TAG, "Kuaidi100 mismatch: OCR=${ocrCodes}, API=${res.pickUpCode}")
                }
            }
        }
        return if (res.success && res.pickUpCode != null && res.pickUpCode in ocrCodes) res else null
    }
    /** 每个 ID 只验证其当前地址，晚到的旧地址结果无法污染新地址。 */
    suspend fun verifySaved(
        context: Context,
        settings: com.pickupcode.app.preferences.AppPreferences.Settings,
        saved: List<RecognitionPipeline.SavedCode>,
        lines: List<com.pickupcode.app.ocr.OCREngine.TextLine>
    ) {
        val repo = com.pickupcode.app.data.AppDatabase.getInstance(context).repository
        for (item in saved.distinctBy { it.id }) {
            val record = repo.getByIdSuspend(item.id) ?: continue
            if (!record.isActive) continue
            try {
                if (settings.enableMapVerify && !record.geoVerified && record.pickupAddress.isNotBlank()) {
                    verifyMap(context, record.pickupAddress, settings.amapApiKey.ifBlank { null }) { confidence, formatted ->
                        repo.updateGeoIfCurrent(record.id, record.pickupAddress, true, confidence, formatted.orEmpty())
                    }
                }
                if (settings.enableKuaidi100 && settings.kuaidi100Key.isNotBlank() && record.type == "pickup_parcel") {
                    val text = com.pickupcode.app.extractor.CodeContext.linesForCode(lines, record.code, saved.map { it.code })
                        .joinToString("\n") { it.text }
                    val tracking = record.trackingNumber.ifBlank { com.pickupcode.app.extractor.BrandResolver.findOrderNumber(text).orEmpty() }
                    if (tracking.isNotBlank()) {
                        val result = verifyKuaidi100(context, settings.kuaidi100Key, tracking, listOf(record.code))
                        if (result?.pickUpCode == record.code && !result.pickUpAddress.isNullOrBlank()) {
                            repo.fillAddressIfBlank(record.id, result.pickUpAddress!!)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                Log.w(TAG, "后置验证失败，保留本地记录")
            }
        }
    }

}
