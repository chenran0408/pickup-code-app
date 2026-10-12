package com.pickupcode.app.ui.screens.home

import com.pickupcode.app.extractor.AddressExtractor
import com.pickupcode.app.extractor.CodeExtractor
import com.pickupcode.app.extractor.CodeValidator
import com.pickupcode.app.ocr.OCREngine

/** 分享文本与通知正文共用预览；裸码仍走原单码入口，验证码不绕过生产识别过滤。 */
object ManualCodeInput {
    private val pickupContext = Regex("来取|取件|取货|取餐|取单|提货|提取码|排号")
    fun isImportText(text: String): Boolean {
        if (text.trimStart().startsWith("当前未取码（")) return true
        val code = CodeExtractor.normalizeText(text.trim())
        if (CodeValidator.isValidManualCode(code, "pickup_parcel") ||
            CodeValidator.isValidManualCode(code, "pickup_food")) return false
        return text.any { it.isWhitespace() || it.code > 127 }
    }

    fun parse(text: String): PendingShareText.ParseResult {
        if (text.length > 30_000) return PendingShareText.ParseResult(error = "文本过长，请分批导入")
        if (text.trimStart().startsWith("当前未取码（")) return PendingShareText.parse(text)
        if (!isImportText(text)) return PendingShareText.ParseResult()
        val lines = text.lines().map { OCREngine.TextLine(CodeExtractor.normalizeText(it), null, null) }
        val entries = com.pickupcode.app.extractor.CodeContext.messageBlocks(lines).flatMap { block ->
            val body = block.joinToString(" ") { it.text }
            if (!pickupContext.containsMatchIn(body) || CodeExtractor.isFinancialNoise(body)) emptyList() else
                CodeExtractor.extract(block, source = "manual").filter {
                    it.type != CodeExtractor.CodeType.coupon && CodeValidator.isValidManualCode(it.code, it.type.name)
                }.map {
                    PendingShareText.Entry(it.code, it.type.name, it.source,
                        AddressExtractor.extractAddressForCode(block, it.code),
                        AddressExtractor.extractCabinetNumber(block, body), expired = false)
                }
        }.distinctBy { it.code to it.type }
        return when {
            entries.size > 300 -> PendingShareText.ParseResult(error = "一次最多导入 300 条")
            entries.isEmpty() -> PendingShareText.ParseResult(error = "未找到取件码或取餐码，请检查通知正文")
            else -> PendingShareText.ParseResult(entries)
        }
    }
}
