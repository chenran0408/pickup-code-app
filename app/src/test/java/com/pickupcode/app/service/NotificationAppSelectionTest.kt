package com.pickupcode.app.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NotificationAppSelectionTest {
    @Test fun `arbitrary selected packages are accepted and others including self summaries and prefix impostors are rejected`() {
        val selected = setOf("com.example.pickup", "com.pickupcode.app")
        fun accept(pkg: String, summary: Boolean = false) = NotificationAppSelection.accepts(
            pkg, "com.android.mms", summary, "com.pickupcode.app", selected)
        assertTrue(accept("com.example.pickup"))
        assertFalse(accept("com.example.pickup.fake"))
        assertFalse(accept("com.taobao.taobao"))
        assertFalse(accept("com.android.mms"))
        assertFalse(accept("com.pickupcode.app"))
        assertFalse(accept("com.example.pickup", true))
    }
    @Test fun `legacy flags migrate and explicit empty selection never resurrects old toggles`() {
        val legacy = NotificationAppSelection.resolve(null, true, true, true, true, true)
        assertEquals(setOf(NotificationAppSelection.DEFAULT_SMS, "com.tencent.mm", "com.taobao.taobao",
            "com.xunmeng.pinduoduo", "com.jingdong.app.mall"), legacy)
        assertTrue(NotificationAppSelection.resolve(emptySet(), true, true, true, true, true).isEmpty())
        assertEquals(setOf("com.example.app"), NotificationAppSelection.resolve(setOf("com.example.app"), true, true, true, true, true))
        assertTrue(NotificationAppSelection.resolve(null, false, false, false, false, false).isEmpty())
    }
    @Test fun `default SMS follows the system choice without enabling unrelated apps`() {
        val selected = setOf(NotificationAppSelection.DEFAULT_SMS)
        fun accept(pkg: String, default: String?) = NotificationAppSelection.accepts(pkg, default, false, "self", selected)
        assertTrue(accept("sms.old", "sms.old"))
        assertFalse(accept("sms.old", "sms.new"))
        assertTrue(accept("sms.new", "sms.new"))
        assertFalse(accept("sms.old", null))
        assertTrue(NotificationAppSelection.contains(selected, "sms.new", "sms.new"))
        assertEquals(NotificationAppSelection.DEFAULT_SMS, NotificationAppSelection.key("sms.new", "sms.new"))
    }
}
