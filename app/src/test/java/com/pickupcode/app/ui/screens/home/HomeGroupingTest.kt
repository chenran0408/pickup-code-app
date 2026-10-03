package com.pickupcode.app.ui.screens.home

import com.pickupcode.app.data.CodeHistory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HomeGroupingTest {
    @Test fun address_groups_normalize_width_whitespace_and_sentence_suffix_only() {
        val a = item(1, "青禾村２４排４号。"); val b = item(2, "青禾村 24 排 4 号")
        val elsewhere = item(3, "青禾村24排5号")
        val groups = HomeGrouping.byAddress(listOf(a, b, elsewhere))
        assertEquals(2, groups.size)
        assertEquals(listOf(1L, 2L), groups.first().second.map { it.id })
    }
    @Test
    fun pending_excludes_coupons_and_archived_records() {
        val parcel = item(1, "长兴路站点").copy(type = "pickup_parcel")
        val food = item(2, "茶饮店").copy(type = "pickup_food")
        val coupon = item(3, "商场").copy(type = "coupon")
        val archived = item(4, "长兴路站点").copy(isActive = false)
        val pending = HomeGrouping.pending(listOf(parcel, food, coupon, archived))
        assertEquals(setOf(1L, 2L), pending.flatMap { it.second }.map { it.id }.toSet())
    }

    @Test
    fun pending_groups_trimmed_addresses_and_keeps_unassigned_last() {
        val groups = HomeGrouping.pending(listOf(item(1, " 长兴路站点 "), item(2, "长兴路站点"), item(3, "")))
        assertEquals(listOf("长兴路站点", ""), groups.map { it.first })
        assertEquals(listOf(1L, 2L), groups.first().second.map { it.id })
    }

    private fun item(id: Long, addr: String) = CodeHistory(
        id = id,
        code = "C$id",
        type = "pickup_parcel",
        source = "韵达",
        rawTextSnippet = "snippet",
        pickupAddress = addr
    )

    @Test
    @DisplayName("按地址聚合：码多的地址排前，空地址归末尾")
    fun group_by_address_order() {
        val items = listOf(
            item(1, "长兴路3号柜"),
            item(2, ""),                  // 空地址
            item(3, "长青街快递柜"),
            item(4, "长兴路3号柜"),
            item(5, "长青街快递柜"),
            item(6, "长青街快递柜")
        )
        val groups = HomeGrouping.byAddress(items)
        // 长青街快递柜(3) > 长兴路3号柜(2) > ""(1)
        assertEquals(listOf("长青街快递柜", "长兴路3号柜", ""), groups.map { it.first })
        assertEquals(3, groups[0].second.size)
        assertEquals(2, groups[1].second.size)
        assertEquals(1, groups[2].second.size)
        // 组内保持原顺序
        assertEquals(listOf(3L, 5L, 6L), groups[0].second.map { it.id })
    }

    @Test
    @DisplayName("全空地址 → 全部归入一组")
    fun group_all_blank() {
        val items = listOf(item(1, "  "), item(2, ""))
        val groups = HomeGrouping.byAddress(items)
        assertEquals(1, groups.size)
        assertEquals("", groups[0].first)   // UI 显示为「未填地址」
        assertEquals(2, groups[0].second.size)
    }

    @Test
    @DisplayName("空列表 → 空分组")
    fun group_empty() {
        assertEquals(0, HomeGrouping.byAddress(emptyList()).size)
    }
}
