package com.pickupcode.app.ui.screens.home

import com.pickupcode.app.data.CodeHistory

/** 分享当前未取码的纯文本；不附带短信原文、截图、运单号或来源 App 信息。 */
object PendingShareText {
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

    private fun String.oneLine(): String = trim().replace(Regex("\\s+"), " ")
}
