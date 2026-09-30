package com.pickupcode.app.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReminderRequestCodesTest {
    private val type = "pickup_parcel"

    @Test
    fun cancellationFindsPreUpgradeExpiryAndCurrentExpiry() {
        // 固定升级前版本产生的请求码；哈希第 29 位为 1 时，新旧到期提醒不同。
        val legacy = 0x7c93a8fe
        val current = 0x5c93a8fe
        assertEquals(current, ReminderRequestCodes.current(type, "8-2-3311", "expiry"))
        assertEquals(setOf(current, legacy),
            ReminderRequestCodes.cancellationCandidates(type, "8-2-3311", "expiry"))
    }

    @Test
    fun unchangedRequestsAreCancelledOnce() {
        assertEquals(setOf(0x548fda71),
            ReminderRequestCodes.cancellationCandidates(type, "123456", "expiry"))
        assertEquals(setOf(0x3c93a8fe),
            ReminderRequestCodes.cancellationCandidates(type, "8-2-3311", "later"))
    }

    @Test
    fun replacingExpiryDoesNotCancelLaterReminder() {
        val later = ReminderRequestCodes.current(type, "8-2-3311", "later")
        val expiry = ReminderRequestCodes.cancellationCandidates(type, "8-2-3311", "expiry")
        assertTrue(later !in expiry)
    }
}
