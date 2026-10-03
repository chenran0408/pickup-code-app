package com.pickupcode.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.pickupcode.app.App
import com.pickupcode.app.BuildConfig
import com.pickupcode.app.data.AppDatabase
import com.pickupcode.app.extractor.AddressExtractor
import com.pickupcode.app.extractor.AIExtractor
import com.pickupcode.app.extractor.CodeExtractor
import com.pickupcode.app.ocr.OCREngine
import com.pickupcode.app.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 短信取件码自动识别（参考同类产品实现）。
 *
 * 输入源补充通道：取件/取餐短信（菜鸟、丰巢、妈妈驿站等几乎都会发短信）到达时，
 * 即使无障碍服务没开、通知栏没弹，也能自动提取取件码并入库通知。
 *
 * 依赖 RECEIVE_SMS 权限（可选开关），不读取历史收件箱。处理流程：
 * 1. 校验开关 + 去重节流（同内容在阈值内不重复处理）
 * 2. 解析短信正文为 OCR 文本行 → CodeExtractor 识别码 + 地址
 * 3. 金融/支付噪音拦截
 * 4. 入库（与无障碍/分享共用 DAO）+ 通知 + 常用站点学习
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return
        Log.d(TAG, "接收到短信广播")

        val extras = intent.getExtras() ?: run { Log.w(TAG, "短信广播无 extras"); return }
        val pdus = extras.get("pdus") as? Array<*> ?: run { Log.w(TAG, "短信广播无 PDU 数组"); return }
        if (pdus.isEmpty()) { Log.w(TAG, "短信广播 PDU 为空"); return }

        // 拼出第一条完整短信（多段 PDU 合体）
        val messages = pdus.mapNotNull { pdu ->
            try {
                SmsMessage.createFromPdu(pdu as? ByteArray, extras.getString("format"))
            } catch (e: Exception) { Log.w(TAG, "短信 PDU 解析失败: ${e.javaClass.simpleName}"); null }
        }
        if (messages.isEmpty()) { Log.w(TAG, "未解析出短信消息，PDU 数量=${pdus.size}"); return }
        var body = messages.joinToString("") { it.messageBody ?: "" }.trim()
        if (body.isEmpty()) { Log.w(TAG, "短信解析后正文为空"); return }
        Log.d(TAG, "短信解析成功，分段数=${messages.size}，正文长度=${body.length}")
        // 去掉发送者的前后重复标题（如 【菜鸟驿站】...【菜鸟驿站】），仅保留正文主体
        val sender = messages.firstOrNull()?.originatingAddress ?: ""
        val displaySender = if (sender.length > 4) "***${sender.takeLast(4)}" else sender
        val rawSnippet = "[短信] $displaySender | $body"

        // 节流：同内容 30s 内不重复处理（持久化到 SharedPreferences——
        // 进程被杀后重投/补发同一条短信时，进程内静态量已归零，会再次走完整管线）
        val now = System.currentTimeMillis()
        val (lastTime, lastBodyHash) = readThrottle(context)
        val bodyHash = "${body.length}:${body.hashCode()}"
        if (now - lastTime < THROTTLE_MS && lastBodyHash == bodyHash) {
            Log.d(TAG, "短信节流跳过（同内容重复）")
            return
        }
        writeThrottle(context, now, bodyHash)

        val pending = goAsync()
        // 复用应用级协程作用域，不再每次广播新建 CoroutineScope（减少临时对象；App.appScope 生命周期与进程一致）
        App.appScope.launch {
            try {
                // H5: goAsync 必须限时完成（广播接收器超时会被系统回收），8s 未完成即放弃，finally 仍会 finish
                val completed = withTimeoutOrNull(8000) {
                    val settings = AppPreferences.observe(context).first()
                    if (!settings.enableSmsReceive) { Log.d(TAG, "短信识别开关关闭"); return@withTimeoutOrNull true }
                    if (!settings.enableParcelCodes && !settings.enableFoodCodes) { Log.d(TAG, "取件/取餐类型均关闭"); return@withTimeoutOrNull true }

                    // 金融/支付噪音拦截（短信里银行/支付通知的数字极易被当取件码）
                    if (CodeExtractor.isFinancialNoise(body)) {
                        Log.d(TAG, "短信为金融/支付类，跳过")
                        return@withTimeoutOrNull true
                    }

                    val lines = body.lines()
                        .map { OCREngine.TextLine(text = it.trim(), boundingBox = null, confidence = 1.0f) }
                        .filter { it.text.isNotBlank() }
                    if (lines.isEmpty()) return@withTimeoutOrNull true

                    val allText = lines.joinToString(" ") { it.text }
                    val startMs = System.currentTimeMillis()
                    val results = CodeExtractor.extract(lines, context = context, source = "sms")
                    // 逐类型过滤：用户单独关闭「取餐码」/「取件码」时，该类型短信不得入库+通知
                    // （与无障碍路径 isTypeEnabled、分享路径 isTypeDisabled 行为对齐）
                    val allResults = results
                        .filter { it.confidence >= settings.confidenceThreshold && isTypeEnabled(it.type, settings) }
                        .toMutableList()
                    Log.d(TAG, "短信识别候选=${results.size}，过滤后=${allResults.size}")

                    val aiEnabled = settings.enableAI && settings.apiKey.isNotBlank()
                    if (allResults.isEmpty() && !aiEnabled) return@withTimeoutOrNull true
                    // 全屏地址（兜底用，各码优先取自己窗口内的地址）
                    val fullAddress = AddressExtractor.extractAddressFromStores(context, lines, allText)
                    val db = AppDatabase.getInstance(context)
                    val repo = db.repository

                    val saved = RecognitionPipeline.finalize(
                        context = context,
                        allResults = allResults.map { it.code to it.type },
                        codeSources = allResults.associate { it.code to it.source },
                        lines = lines,
                        allText = allText,
                        fullAddress = fullAddress,
                        rawSnippet = rawSnippet,
                        timestamp = now,
                        repo = repo
                    )
                    for (s in saved) {
                        RecognitionPipeline.notifySaved(context, { repo.countDuplicateGroups() },
                            s.code, s.type, s.source, s.id, s.existed)
                        RecognitionPipeline.logSaved(TAG, s.code, s.type, s.source, s.address, s.existed)
                    }
                    if (saved.isNotEmpty()) App.appScope.launch(Dispatchers.IO) {
                        PostVerifier.verifySaved(context.applicationContext, settings, saved, lines)
                    }
                    if (aiEnabled) BackgroundAiEnricher.start(context, settings, lines, allText,
                        allResults.map { it.code to it.type }, allResults.associate { it.code to it.source }, saved, fullAddress)

                    Log.d(TAG, "短信处理完成，保存记录数=${saved.size}")
                    true
                }
                if (completed == null) Log.w(TAG, "短信处理超过8秒，已取消")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // 协程取消必须重抛，不能吞
            } catch (e: Exception) {
                Log.e(TAG, "短信识别失败: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** 该类型是否被用户开启（统一走 RecognitionPipeline，避免三份 switch 漂移）。 */
    private fun isTypeEnabled(type: CodeExtractor.CodeType, settings: AppPreferences.Settings): Boolean =
        RecognitionPipeline.isTypeEnabled(type, settings)

    /** 节流状态持久化读写（进程重启后仍生效）。存 body 的「长度:hash」而非原文，避免长短信占空间。 */
    private fun readThrottle(context: Context): Pair<Long, String> {
        val sp = context.getSharedPreferences("sms_throttle", Context.MODE_PRIVATE)
        return sp.getLong("last_time", 0L) to (sp.getString("last_body", "") ?: "")
    }

    private fun writeThrottle(context: Context, time: Long, bodyHash: String) {
        context.getSharedPreferences("sms_throttle", Context.MODE_PRIVATE)
            .edit().putLong("last_time", time).putString("last_body", bodyHash).apply()
    }

    private companion object {
        const val TAG = "SmsReceiver"
        const val THROTTLE_MS = 30_000L
    }
}

