package com.pickupcode.app.ui.screens.home

import com.pickupcode.app.data.CodeHistory

/** 在后台一次生成当前显示方式需要的列表，避免在组合时重复排序和同时生成两套分组。 */
data class HomeListProjection(
    val records: List<CodeHistory> = emptyList(),
    val addressGroups: List<Pair<String, List<CodeHistory>>> = emptyList(),
    val timeGroups: Map<String, List<CodeHistory>> = emptyMap()
) {
    companion object {
        fun build(active: List<CodeHistory>, completed: List<CodeHistory>, type: String,
            completedOnly: Boolean, expiredOnly: Boolean, mode: String, now: Long,
            todayStart: Long, yesterdayStart: Long): HomeListProjection {
            val records = (if (completedOnly) completed else if (expiredOnly) active else active + completed)
                .asSequence().filter { record ->
                    (!expiredOnly || record.expiryTime in 1..now) && when (type) {
                        "food" -> record.type == "pickup_food"
                        "parcel" -> record.type == "pickup_parcel"
                        "coupon" -> record.type == "coupon"
                        else -> true
                    }
                }.sortedByDescending { it.timestamp }.toList()
            return if (mode == "address") HomeListProjection(records, HomeGrouping.byAddress(records))
                else HomeListProjection(records, timeGroups = records.groupBy {
                    when { it.timestamp >= todayStart -> "今天"; it.timestamp >= yesterdayStart -> "昨天"; else -> "更早" }
                })
        }
    }
}
