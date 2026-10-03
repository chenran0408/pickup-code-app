package com.pickupcode.app.service

import android.content.Context
import android.util.Log
import com.pickupcode.app.BuildConfig
import com.pickupcode.app.data.CodeHistory
import com.pickupcode.app.data.CodeRepository
import com.pickupcode.app.extractor.AddressExtractor
import com.pickupcode.app.extractor.CodeExtractor
import com.pickupcode.app.extractor.ExpiryExtractor
import com.pickupcode.app.learner.CommonStationStore
import com.pickupcode.app.notification.CodeNotificationManager
import com.pickupcode.app.ocr.OCREngine
import com.pickupcode.app.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 识别后处理管线——三条路径（无障碍/分享/短信）共用的「逐码落库 + 通知」逻辑。
 * 每个码：取卡片窗口地址 → 取柜号 → 保存 → 常用站点学习 → 通知。
 *
 * 路径差异通过参数注入（screenshotPath / shareSource / timestamp / 通知回调），
 * 核心顺序三路径完全一致。
 */
object RecognitionPipeline {

    /** 单码处理结果：code + 保存 id + 是否重复。 */
    data class SavedCode(val code: String, val type: CodeExtractor.CodeType, val source: String,
                         val id: Long, val existed: Boolean, val address: String)

    /**
     * 逐码处理所有识别结果：取地址 → 取柜号 → 落库 → 常用站点学习。
     * 通知由调用方基于返回的 [SavedCode] 列表自行分发（三路径通知策略不同）。
     * @param allResults 识别出的 (code, type) 列表
     * @param codeSources code → 来源名（品牌/驿站）
     * @param lines OCR 行（逐码窗口地址用）
     * @param allText OCR 全文（柜号提取 + 站点学习用）
     * @param fullAddress 全屏兜底地址
     * @param rawSnippet 入库的原始文本片段
     * @param screenshotPath 截图路径（短信路径为空串）
     * @param shareSourcePkg/Name 分享来源（短信/无障碍为空串）
     * @param timestamp 入库时间戳
     * @param aiAddressHints AI（视觉通道）给出的 code→地址/站点，本地空缺时采用，冲突时保存为候选供用户核对
     * @param aiCabinetHints AI 给出的 code→柜号，仅在本地取不到柜号时采用
     * @return 保存的码列表（供通知分发 / Kuaidi100 回填等后续使用）
     */
    suspend fun finalize(
        context: Context,
        allResults: List<Pair<String, CodeExtractor.CodeType>>,
        codeSources: Map<String, String>,
        lines: List<OCREngine.TextLine>,
        allText: String,
        fullAddress: String,
        rawSnippet: String,
        screenshotPath: String = "",
        shareSourcePkg: String = "",
        shareSourceName: String = "",
        timestamp: Long = System.currentTimeMillis(),
        aiAddressHints: Map<String, String> = emptyMap(),
        aiCabinetHints: Map<String, String> = emptyMap(),
        repo: CodeRepository,
        aiCodes: Set<String> = emptySet(),
        targetIds: Map<String, Long> = emptyMap()
    ): List<SavedCode> {
        // 🔒 兜底：身份码/出库码页面一律不入库（截图拒采在无障碍路径已做，这里防其它入口漏网）
        if (com.pickupcode.app.util.SensitivePageGuard.isIdentityCodePage(allText)) {
            android.util.Log.w("RecognitionPipeline", "身份码/出库码页面，拒绝入库")
            return emptyList()
        }
        val validationText = allText.ifBlank { rawSnippet }
        if (com.pickupcode.app.extractor.AuthenticationCodeFilter.isAuthenticationOnly(validationText)) return emptyList()
        val saved = mutableListOf<SavedCode>()
        val seen = mutableSetOf<String>()
        // 同屏码数（多码时位置类证据要防串台）
        val multiCode = allResults.distinctBy { "${it.first}|${it.second}" }.size > 1
        // 全屏兜底仲裁只算一次（避免多码同屏每码重跑全量 extractLocation）：
        // 单码同屏全屏地址必然属于本卡，几何兜底照常采信；多码同屏防串台仅采信文本证据型来源
        val fallbackAddr = AddressExtractor.resolveAddress(
            lines, allText, perCodeAddr = "", fullAddress = fullAddress,
            multiCodeOnScreen = multiCode
        )
        val savedAddresses = com.pickupcode.app.learner.SavedAddressStore.matcherViews(context)
        val replacedPaths = mutableListOf<String>()
        repo.retainScreenshot(screenshotPath)
        try {
        for ((code, type) in allResults) {
            // AI、分享和其他自动入口统一兜底，过滤不能仅依赖短信入口的金融关键词。
            if (com.pickupcode.app.extractor.AuthenticationCodeFilter.reject(code, validationText)) continue
            val key = "$code|$type"
            if (key in seen) continue
            seen.add(key)
            val source = codeSources[code] ?: "unknown"

            val codeLines = com.pickupcode.app.extractor.CodeContext.linesForCode(lines, code, allResults.map { it.first })
            val codeText = codeLines.joinToString("\n") { it.text }
            val savedAddr = com.pickupcode.app.extractor.SavedAddressMatcher.match(codeLines, savedAddresses)?.fullName.orEmpty()
            val perCodeAddr = AddressExtractor.extractAddressForCode(codeLines, code)
                .ifBlank { if (codeLines.isEmpty()) "" else AddressExtractor.extractLocation(codeLines, codeText).fullAddress }
            val localAddr = perCodeAddr.ifBlank { if (multiCode) "" else fallbackAddr }
            val aiAddr = aiAddressHints[code].orEmpty()
            // 本地已有明确地址时保留它，AI 的冲突结果作为候选供用户核对。
            val effAddr = savedAddr.ifBlank { localAddr.ifBlank { aiAddr } }
            val addrOrigin = when {
                savedAddr.isNotBlank() -> "saved"
                localAddr.isNotBlank() -> "local"
                aiAddr.isNotBlank() -> "ai"
                else -> "local"
            }
            val suggested = aiAddr.takeIf { it.isNotBlank() && it != effAddr }.orEmpty()
            val cabinet = if (type == CodeExtractor.CodeType.pickup_parcel) {
                AddressExtractor.extractCabinetNumber(codeLines, codeText).ifBlank { aiCabinetHints[code].orEmpty() }
            } else ""
            val expiryTime = ExpiryExtractor.expiryTimeFor(codeText, type, timestamp) ?: 0L
            val history = CodeHistory(
                code = code,
                type = type.name,
                source = source,
                rawTextSnippet = sanitizeSnippet(codeText.ifBlank { if (multiCode) "" else rawSnippet }),
                pickupAddress = effAddr,
                cabinetNumber = cabinet,
                screenshotPath = screenshotPath,
                shareSourcePkg = shareSourcePkg,
                shareSourceName = shareSourceName,
                timestamp = timestamp,
                expiryTime = expiryTime,
                trackingNumber = com.pickupcode.app.extractor.BrandResolver.findOrderNumber(codeText).orEmpty(),
                recognitionOrigin = if (code in aiCodes) "ai" else "local",
                addressOrigin = addrOrigin,
                suggestedAddress = suggested
            )
            val targetId = targetIds[key]
            val existingId = targetId ?: repo.findMergeTarget(history)?.id
            if (existingId != null) {
                // 兼容升级前保存在学习偏好中的人工确认，首次识别时转为数据库字段保护。
                val learner = com.pickupcode.app.learner.PatternLearner
                if (learner.isCodeConfirmed(context, existingId)) repo.protectField(existingId, com.pickupcode.app.data.RecordMergePolicy.CODE)
                if (learner.isSourceConfirmed(context, existingId)) repo.protectField(existingId, com.pickupcode.app.data.RecordMergePolicy.SOURCE)
                if (learner.isAddrConfirmed(context, existingId)) repo.protectField(existingId, com.pickupcode.app.data.RecordMergePolicy.ADDRESS)
            }
            if (targetId != null) {
                // 回填不再次创建通知或记录，并在事务中复查人工修改与归档状态。
                repo.enrichIfCurrent(targetId, history.copy(screenshotPath = ""))
                continue
            }
            val save = repo.save(history)
            if (save.replacedScreenshotPath.isNotBlank()) replacedPaths.add(save.replacedScreenshotPath)
            val stored = repo.getByIdSuspend(save.id) ?: continue
            saved.add(SavedCode(stored.code, type, stored.source, save.id, save.existed, stored.pickupAddress))

            // 到期提醒排程：重复识别（existed）也重排——同码第二条短信可能带来新时限，
            // 若只在 !existed 时排程会漏掉更新后的提醒（FLAG_UPDATE_CURRENT 天然覆盖旧闹钟）
            if (stored.expiryTime > 0 && AppPreferences.isExpiryRemindEnabled(context)) {
                CodeNotificationManager.scheduleExpiryReminder(context, stored.code, type, stored.source, stored.expiryTime, historyId = save.id)
            }

            // 常用站点学习：带地址的取件记录累计站点频次
            if (type == CodeExtractor.CodeType.pickup_parcel && effAddr.isNotBlank()) {
                CommonStationStore.recordCode(context, effAddr, codeText)
            }
        }
        withContext(Dispatchers.IO) { repo.releaseScreenshots(replacedPaths) }
        return saved
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                repo.releaseScreenshotLease(screenshotPath)
            }
        }
    }

    /** 通知分发：同码同 type 已存在 → 重复提示；否则正常通知。 */
    suspend fun notifySaved(context: Context, dupCountProvider: suspend () -> Int,
                            code: String, type: CodeExtractor.CodeType, source: String, id: Long, existed: Boolean) {
        if (existed) {
            val dupCount = dupCountProvider()
            CodeNotificationManager.showDuplicate(context, code, type, source, id, dupCount)
        } else {
            CodeNotificationManager.show(context, code, type, source, id)
        }
    }

    // 手机号（1开头 11 位）
    private val MOBILE_REGEX = Regex("1[3-9]\\d{9}")

    /** 脱敏：掩码手机号 + 截断，避免全屏/短信原文带 PII 入库（H-A）。 */
    private fun sanitizeSnippet(text: String?, maxLen: Int = 200): String {
        if (text.isNullOrBlank()) return ""
        val masked = MOBILE_REGEX.replace(text) { m ->
            val d = m.value
            d.take(3) + "****" + d.takeLast(4)
        }
        return masked.take(maxLen)
    }

    /** 识别日志（三路径统一格式）。 */
    fun logSaved(tag: String, code: String, type: CodeExtractor.CodeType, source: String, address: String, existed: Boolean) {
        // H4: release 不落 PII（码/地址/来源）
        if (BuildConfig.DEBUG) {
            Log.d(tag, "识别入库: $code (${type.name}) from $source @ $address${if (existed) " [DUPLICATE]" else ""}")
        }
    }

    /** 该类型是否被用户开启（三路径共用；替换 Sms/Share/Accessibility 三份 switch 副本）。 */
    fun isTypeEnabled(type: CodeExtractor.CodeType, settings: AppPreferences.Settings): Boolean = when (type) {
        CodeExtractor.CodeType.pickup_food -> settings.enableFoodCodes
        CodeExtractor.CodeType.pickup_parcel -> settings.enableParcelCodes
        CodeExtractor.CodeType.coupon -> settings.enableCouponCodes
    }

}
