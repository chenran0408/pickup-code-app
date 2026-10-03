package com.pickupcode.app.ui.screens.home

import com.pickupcode.app.data.CodeHistory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HomeListProjectionTest {
    private fun item(id: Long, timestamp: Long, expiry: Long = 0, type: String = "pickup_parcel") =
        CodeHistory(id = id, code = "7-3-5268", type = type, source = "圆通", rawTextSnippet = "合成性能测试", timestamp = timestamp,
            expiryTime = expiry, pickupAddress = "青禾站")

    @Test fun `all includes completed and coupons while expiry boundary excludes no deadline`() {
        val active = listOf(item(1, 120, 200), item(2, 110, type = "coupon"), item(3, 100, 201))
        val done = listOf(item(4, 130).copy(isActive = false, archiveKind = "done"))
        fun project(type: String = "all", complete: Boolean = false, expired: Boolean = false) =
            HomeListProjection.build(active, done, type, complete, expired, "address", 200, 100, 50)
        assertEquals(listOf(4L, 1L, 2L, 3L), project().records.map { it.id })
        assertEquals(listOf(1L), project(expired = true).records.map { it.id })
        assertEquals(listOf(4L), project(complete = true).records.map { it.id })
        assertEquals(listOf(2L), project(type = "coupon").records.map { it.id })
        assertTrue(project().timeGroups.isEmpty())
    }

    @Test fun `time boundaries and large history retain each record once`() {
        val input = (1L..10_000L).map { item(it, it) }
        val result = HomeListProjection.build(input, emptyList(), "all", false, false, "time", 10000, 9000, 8000)
        assertEquals(10000, result.records.size)
        assertEquals(10000, result.timeGroups.values.sumOf { it.size })
        assertEquals(1001, result.timeGroups.getValue("今天").size)
        assertEquals(1000, result.timeGroups.getValue("昨天").size)
        assertEquals(10000L, result.records.first().id)
        assertTrue(result.addressGroups.isEmpty())
    }
}
