package com.pickupcode.app.service

import android.content.Context
import android.widget.Toast
import com.pickupcode.app.App
import com.pickupcode.app.data.AppDatabase
import com.pickupcode.app.extractor.AIExtractor
import com.pickupcode.app.extractor.CodeExtractor
import com.pickupcode.app.ocr.OCREngine
import com.pickupcode.app.preferences.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 本地先保存并通知，AI 独立补充；回填按本次记录 ID 复查，不覆盖人工编辑。 */
object BackgroundAiEnricher {
    fun start(
        context: Context,
        settings: AppPreferences.Settings,
        lines: List<OCREngine.TextLine>,
        allText: String,
        localResults: List<Pair<String, CodeExtractor.CodeType>>,
        localSources: Map<String, String>,
        saved: List<RecognitionPipeline.SavedCode>,
        fullAddress: String,
        screenshotPath: String = "",
        imageBase64: String? = null,
        shareSourcePkg: String = "",
        shareSourceName: String = "",
        showErrors: Boolean = false
    ) {
        val appContext = context.applicationContext
        if (!settings.enableAI || settings.apiKey.isBlank()) return
        if (com.pickupcode.app.util.SensitivePageGuard.isIdentityCodePage(allText)) return
        if (com.pickupcode.app.extractor.AuthenticationCodeFilter.isAuthenticationOnly(allText)) return
        val recognizedAt = System.currentTimeMillis()
        val repo = AppDatabase.getInstance(appContext).repository
        repo.retainScreenshot(screenshotPath)
        App.appScope.launch(Dispatchers.IO) {
            try {
                val imageMode = !imageBase64.isNullOrBlank()
                val response = withTimeoutOrNull(if (imageMode) 25_000L else 8_000L) {
                    if (imageMode) AIExtractor.extractFromImage(imageBase64!!, settings.apiKey,
                        settings.apiBaseUrl, settings.apiModel, allText)
                    else AIExtractor.extract(allText, settings.apiKey, settings.apiBaseUrl, settings.apiModel)
                }
                val error = response?.error ?: if (response == null) "AI服务超时" else null
                if (error != null && showErrors) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, if (saved.isEmpty()) "未识别到码 · ${AIExtractor.categorizeError(error)}" else "本地识别已保存 · ${AIExtractor.categorizeError(error)}", Toast.LENGTH_SHORT).show()
                    }
                }
                val ai = response?.results.orEmpty().filter { RecognitionPipeline.isTypeEnabled(it.type, settings) }
                // 对已识别码的类型冲突保留原记录；AI 新码标明来源，供详情页核对。
                val accepted = ai.filter { candidate ->
                    localResults.none { it.first == candidate.code && it.second != candidate.type }
                }
                val combined = (localResults + accepted.map { it.code to it.type }).distinct()
                if (accepted.isNotEmpty()) {
                    val additional = RecognitionPipeline.finalize(
                        context = appContext, allResults = combined,
                        codeSources = accepted.associate { it.code to it.source } + localSources,
                        lines = lines, allText = allText, fullAddress = fullAddress,
                        rawSnippet = allText, screenshotPath = screenshotPath,
                        shareSourcePkg = shareSourcePkg, shareSourceName = shareSourceName,
                        timestamp = recognizedAt,
                        aiAddressHints = accepted.associate { it.code to it.address.ifBlank { it.station } },
                        aiCabinetHints = accepted.associate { it.code to it.cabinet },
                        repo = repo,
                        aiCodes = accepted.map { it.code }.toSet(),
                        targetIds = saved.associate { "${it.code}|${it.type}" to it.id }
                    )
                    additional.forEach { item ->
                        RecognitionPipeline.notifySaved(appContext, { repo.countDuplicateGroups() },
                            item.code, item.type, item.source, item.id, item.existed)
                    }
                    PostVerifier.verifySaved(appContext, settings, saved + additional, lines)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (showErrors) withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "AI 补充失败，已保存的本地结果仍可使用", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) {
                    repo.releaseScreenshotLease(screenshotPath)
                    repo.releaseScreenshots(listOf(screenshotPath))
                }
            }
        }
    }
}
