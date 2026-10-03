package com.pickupcode.app.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecordMergePolicyTest {
    private fun record(address: String = "东路店", source: String = "茶饮", timestamp: Long = 100_000) =
        CodeHistory(code = "A12", type = "pickup_food", source = source, pickupAddress = address,
            rawTextSnippet = "取餐码A12", timestamp = timestamp)

    @Test fun `different shops with same short code remain separate`() {
        assertFalse(RecordMergePolicy.samePickup(record(), record("西路店")))
        assertFalse(RecordMergePolicy.samePickup(record(source = "甲店"), record(source = "乙店")))
    }

    @Test fun `same shop recent rescan merges but code reuse next day does not`() {
        assertTrue(RecordMergePolicy.samePickup(record(), record(timestamp = 120_000)))
        assertFalse(RecordMergePolicy.samePickup(record(), record(timestamp = 86_500_000)))
    }

    @Test fun `insufficient evidence preserves both records`() {
        assertFalse(RecordMergePolicy.samePickup(record("", "unknown").copy(rawTextSnippet = ""),
            record("", "unknown").copy(rawTextSnippet = "")))
    }

    @Test fun `same chain brand without a shop is insufficient evidence`() {
        assertFalse(RecordMergePolicy.samePickup(record("", "茶饮"), record("", "茶饮")))
    }

    @Test fun `tracking identity prevents unrelated parcel merge`() {
        val parcel = record().copy(type = "pickup_parcel", trackingNumber = "SF246813579024")
        assertFalse(RecordMergePolicy.samePickup(parcel, parcel.copy(trackingNumber = "SF135792468013")))
        assertTrue(RecordMergePolicy.samePickup(parcel, parcel.copy(pickupAddress = "更新地址")))
    }

    @Test fun `manual edits survive automatic updates`() {
        val previous = record().copy(cabinetNumber = "2号柜", userEditedFields = RecordMergePolicy.SOURCE or RecordMergePolicy.ADDRESS or RecordMergePolicy.CABINET)
        val merged = RecordMergePolicy.merge(previous, record("错地址", "unknown").copy(cabinetNumber = "9号柜"))
        assertEquals(previous.pickupAddress, merged.pickupAddress)
        assertEquals(previous.source, merged.source)
        assertEquals(previous.cabinetNumber, merged.cabinetNumber)
    }

    @Test fun `AI conflict is a suggestion and does not replace verified local address`() {
        val old = record().copy(geoVerified = true, geoConfidence = 0.9f)
        val merged = RecordMergePolicy.merge(old, record("另一店").copy(addressOrigin = "ai"))
        assertEquals("东路店", merged.pickupAddress)
        assertEquals("另一店", merged.suggestedAddress)
        assertTrue(merged.geoVerified)
    }

    @Test fun `address change invalidates prior map verification`() {
        val old = record().copy(geoVerified = true, geoConfidence = 1f, geoFormattedAddress = "东路店规范地址")
        val merged = RecordMergePolicy.merge(old, record("西路店"))
        assertFalse(merged.geoVerified)
        assertEquals(0f, merged.geoConfidence)
        assertEquals("", merged.geoFormattedAddress)
    }

    @Test fun `repeat recognition does not postpone deadline or original creation time`() {
        val previous = record().copy(expiryTime = 200_000)
        val merged = RecordMergePolicy.merge(previous, record(timestamp = 120_000).copy(expiryTime = 300_000))
        assertEquals(200_000, merged.expiryTime)
        assertEquals(previous.timestamp, merged.timestamp)
    }
}
