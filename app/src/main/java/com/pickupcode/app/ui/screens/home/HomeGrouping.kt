package com.pickupcode.app.ui.screens.home

import com.pickupcode.app.data.CodeHistory

/**
 * 主页分组纯逻辑（可单测）。
 */
object HomeGrouping {

    /** 未取的食/件码按地点组成待取清单，券码继续在普通列表中查看。 */
    fun pending(items: List<CodeHistory>): List<Pair<String, List<CodeHistory>>> = byAddress(
        items.filter { it.isActive && it.type in setOf("pickup_parcel", "pickup_food") }
    )

    /**
     * 按取件地址聚合：空地址归入末尾（UI 显示为「未填地址」）；
     * 非空地址按该地址待取码数量降序（要取的码最多的地址排最前）。
     * 组内顺序保持原列表顺序（组内按时间先后）。
     */
    fun byAddress(items: List<CodeHistory>): List<Pair<String, List<CodeHistory>>> =
        items.groupBy { com.pickupcode.app.data.AddressNormalizer.key(it.pickupAddress) }
            .entries
            .sortedWith(compareBy({ it.key.isEmpty() }, { -it.value.size }))
            .map { entry -> entry.value.first().pickupAddress.trim().trimEnd('。', '.', '，', ',', '；', ';') to entry.value }
}
