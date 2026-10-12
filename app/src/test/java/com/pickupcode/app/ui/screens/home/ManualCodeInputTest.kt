package com.pickupcode.app.ui.screens.home

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ManualCodeInputTest {
    @Test fun station_sms_opens_preview_with_full_code_and_address() {
        val text = "【驿小哥】您的顺丰包裹已到青禾市科技大学七公寓顺丰站点，请18:00前凭A3-52786来取，谢谢。"
        assertTrue(ManualCodeInput.isImportText(text))
        val parsed = ManualCodeInput.parse(text)
        assertNull(parsed.error)
        assertEquals("A3-52786", parsed.entries.single().code)
        assertEquals("pickup_parcel", parsed.entries.single().type)
        assertTrue(parsed.entries.single().address.contains("顺丰站点"))
    }

    @Test fun plain_code_keeps_single_entry_mode() {
        for (code in listOf("A3-52786", "Ａ３－５２７８６", "7-3-5268", "82"))
            assertFalse(ManualCodeInput.isImportText(code), code)
    }

    @Test fun multiple_notifications_preview_and_deduplicate() {
        val text = "【驿小哥】顺丰包裹已到青禾站，请凭A3-52786来取。" +
            "【菜鸟驿站】请凭7-3-5268取件。【驿小哥】顺丰包裹已到青禾站，请凭A3-52786来取。"
        assertEquals(setOf("A3-52786", "7-3-5268"), ManualCodeInput.parse(text).entries.map { it.code }.toSet())
    }

    @Test fun authentication_and_delivery_only_messages_do_not_import() {
        for (text in listOf("【腾讯科技】登录验证码184538，请勿泄露。",
            "【顺丰】快件正在配送，运单号SF123456789012，请注意查收。",
            "信用卡交易52786元，授权码675839。", "设备型号H-52786，库存675839。")) {
            val parsed = ManualCodeInput.parse(text)
            assertTrue(parsed.entries.isEmpty(), text)
            assertNotNull(parsed.error, text)
        }
    }

    @Test fun exported_text_still_uses_strict_parser() {
        val text = "当前未取码（1条）\n\n青禾站\n· [取件] 顺丰：A3-52786"
        assertEquals("A3-52786", ManualCodeInput.parse(text).entries.single().code)
        assertNotNull(ManualCodeInput.parse(text.replace("1条", "2条")).error)
        assertNotNull(ManualCodeInput.parse("x".repeat(30_001)).error)
    }
}
