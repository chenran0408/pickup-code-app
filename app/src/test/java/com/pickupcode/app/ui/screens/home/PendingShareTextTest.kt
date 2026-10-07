package com.pickupcode.app.ui.screens.home

import com.pickupcode.app.data.CodeHistory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PendingShareTextTest {
    @Test fun shares_only_active_pickup_records_and_no_private_source_text() {
        val parcel = record(1, "7-3-5268", "pickup_parcel", "圆通", "青禾村24排4号")
            .copy(cabinetNumber = "2号柜", rawTextSnippet = "保密短信正文", trackingNumber = "保密运单")
        val food = record(2, "A108", "pickup_food", "", "")
            .copy(expiryTime = 99)
        val completed = record(3, "DONE", "pickup_parcel", "圆通", "青禾村24排4号")
            .copy(isActive = false, archiveKind = "done")
        val coupon = record(4, "COUPON", "coupon", "商店", "")

        val text = PendingShareText.build(listOf(parcel, food, completed, coupon), now = 100)!!

        assertEquals("当前未取码（2条）\n\n青禾村24排4号\n· [取件] 圆通：7-3-5268（2号柜）\n\n未填地址\n· [取餐] 取餐：A108 · 已过期", text)
        assertFalse(text.contains("保密"))
        assertFalse(text.contains("DONE"))
        assertFalse(text.contains("COUPON"))
        val parsed = PendingShareText.parse(text)
        assertNull(parsed.error)
        assertEquals(listOf("pickup_parcel", "pickup_food"), parsed.entries.map { it.type })
        assertEquals(listOf("青禾村24排4号", ""), parsed.entries.map { it.address })
        assertEquals(listOf("2号柜", ""), parsed.entries.map { it.cabinet })
        assertEquals(listOf(false, true), parsed.entries.map { it.expired })
    }

    @Test fun no_pickup_records_returns_null() {
        assertNull(PendingShareText.build(listOf(
            record(1, "DONE", "pickup_parcel", "圆通", "").copy(isActive = false),
            record(2, "COUPON", "coupon", "商店", "")
        ), now = 100))
    }

    @Test fun legacy_text_is_readable_and_invalid_or_partial_text_is_rejected() {
        val old = "当前未取码（1条）\n\n青禾村24排4号\n· 圆通：7-3-5268"
        val parsed = PendingShareText.parse(old)
        assertNull(parsed.error)
        assertEquals(1, parsed.legacyCount)
        assertEquals("pickup_parcel", parsed.entries.single().type)
        assertEquals(emptyList<PendingShareText.Entry>(), PendingShareText.parse(old.replace("1条", "2条")).entries)
        assertEquals(emptyList<PendingShareText.Entry>(), PendingShareText.parse("验证码：184539").entries)
    }

    private fun record(id: Long, code: String, type: String, source: String, address: String) = CodeHistory(
        id = id, code = code, type = type, source = source, pickupAddress = address,
        rawTextSnippet = "private original text"
    )
}
