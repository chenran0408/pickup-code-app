package com.pickupcode.app.ui.screens.home

import com.pickupcode.app.data.CodeHistory
import com.pickupcode.app.extractor.CodeValidator

/** 分享当前未取码的纯文本；不附带短信原文、截图、运单号或来源 App 信息。 */
object PendingShareText {
    data class Entry(
        val code: String,
        val type: String,
        val source: String,
        val address: String,
        val cabinet: String,
        val expired: Boolean,
        val legacy: Boolean = false
    )

    data class ParseResult(val entries: List<Entry> = emptyList(), val error: String? = null) {
        val legacyCount: Int get() = entries.count { it.legacy }
    }

    fun build(records: List<CodeHistory>, now: Long): String? {
        val pending = records.filter {
            it.isActive && it.type in setOf("pickup_parcel", "pickup_food")
        }
        if (pending.isEmpty()) return null

        val groups = HomeGrouping.byAddress(pending)
        return buildString {
            append("当前未取码（${pending.size}条）")
            groups.forEach { (address, items) ->
                append("\n\n")
                append(address.ifBlank { "未填地址" }.oneLine())
                items.forEach { item ->
                    append("\n· ")
                    append(if (item.type == "pickup_food") "[取餐] " else "[取件] ")
                    append(item.source.ifBlank {
                        if (item.type == "pickup_food") "取餐" else "取件"
                    }.oneLine())
                    append("：")
                    append(item.code.oneLine())
                    if (item.cabinetNumber.isNotBlank()) {
                        append("（${item.cabinetNumber.oneLine()}）")
                    }
                    if (item.expiryTime in 1..now) append(" · 已过期")
                }
            }
        }
    }

    /** 只解析本应用分享格式，整段校验通过后才允许导入，避免把聊天正文误当多条码。 */
    fun parse(text: String): ParseResult {
        if (text.length > 30_000) return ParseResult(error = "文本过长，请分批导入")
        val lines = text.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val count = Regex("^当前未取码（(\\d{1,3})条）$").matchEntire(lines.firstOrNull().orEmpty())
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: return ParseResult(error = "请粘贴“分享当前未取码”生成的完整文本")
        if (count !in 1..300) return ParseResult(error = "一次最多导入 300 条")

        val entries = ArrayList<Entry>(count)
        var address: String? = null
        for (line in lines.drop(1)) {
            if (!line.startsWith("· ")) {
                if (line.length > 160) return ParseResult(error = "地址过长，请检查分享文本")
                address = line.takeUnless { it == "未填地址" }.orEmpty()
                continue
            }
            val place = address ?: return ParseResult(error = "码值前缺少地址")
            var body = line.removePrefix("· ")
            val type = when {
                body.startsWith("[取件] ") -> { body = body.removePrefix("[取件] "); "pickup_parcel" }
                body.startsWith("[取餐] ") -> { body = body.removePrefix("[取餐] "); "pickup_food" }
                else -> null
            }
            val separator = body.lastIndexOf('：')
            if (separator !in 1 until body.lastIndex) return ParseResult(error = "第 ${entries.size + 1} 条格式不完整")
            val source = body.substring(0, separator).trim()
            var value = body.substring(separator + 1).trim()
            val expired = value.endsWith(" · 已过期")
            if (expired) value = value.removeSuffix(" · 已过期")
            val cabinetMatch = Regex("^(.+?)（([^（）]+)）$").matchEntire(value)
            val code = (cabinetMatch?.groupValues?.get(1) ?: value).trim()
            val cabinet = cabinetMatch?.groupValues?.get(2).orEmpty().trim()
            val resolvedType = type ?: if (source == "取餐" ||
                !CodeValidator.isValidManualCode(code, "pickup_parcel") && CodeValidator.isValidManualCode(code, "pickup_food")
            ) "pickup_food" else "pickup_parcel"
            if (source.isEmpty() || source.length > 60 || cabinet.length > 60 ||
                !CodeValidator.isValidManualCode(code, resolvedType)
            ) return ParseResult(error = "第 ${entries.size + 1} 条码值或来源无效，请检查后重试")
            entries += Entry(code, resolvedType, source, place, cabinet, expired, legacy = type == null)
            if (entries.size > count) return ParseResult(error = "条数与分享标题不一致")
        }
        if (entries.size != count) return ParseResult(error = "条数与分享标题不一致")
        return ParseResult(entries)
    }

    private fun String.oneLine(): String = trim().replace(Regex("\\s+"), " ")
}
