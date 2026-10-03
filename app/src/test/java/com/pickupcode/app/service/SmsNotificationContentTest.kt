package com.pickupcode.app.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SmsNotificationContentTest {
    @Test fun `only default SMS application non summary notices are accepted`() {
        assertTrue(SmsNotificationContent.acceptPackage("com.android.mms", "com.android.mms", false))
        assertFalse(SmsNotificationContent.acceptPackage("com.bank", "com.android.mms", false))
        assertFalse(SmsNotificationContent.acceptPackage("com.pickupcode.app", "com.android.mms", false))
        assertFalse(SmsNotificationContent.acceptPackage("com.android.mms", null, false))
        assertFalse(SmsNotificationContent.acceptPackage("com.android.mms", "com.android.mms", true))
    }
    @Test fun `only newest incoming message is used without old conversation codes`() {
        val messages = listOf(SmsNotificationContent.Message("旧取件码3-7-4162", true),
            SmsNotificationContent.Message("新取件码9-4-1526", true))
        assertEquals("新取件码9-4-1526", SmsNotificationContent.body("摘要", "聊天全文", emptyList(), messages))
    }
    @Test fun `latest sent message cannot create a pickup record`() {
        assertEquals("", SmsNotificationContent.body("取件码3-7-4162", null, emptyList(),
            listOf(SmsNotificationContent.Message("取件码3-7-4162", false))))
    }
    @Test fun `expanded body takes priority and inbox style only uses newest row`() {
        assertEquals("完整正文", SmsNotificationContent.body("截断", "完整正文", emptyList(), emptyList()))
        assertEquals("最新", SmsNotificationContent.body(null, null, listOf("历史", "最新"), emptyList()))
        assertEquals("", SmsNotificationContent.body(null, null, emptyList(), emptyList()))
    }
    @Test fun `large notification bodies are bounded`() {
        assertEquals(20000, SmsNotificationContent.body("a".repeat(25000), null, emptyList(), emptyList()).length)
    }

    @Test fun `wechat switch is independent and package must match exactly`() {
        assertTrue(SmsNotificationContent.acceptPackage("com.tencent.mm", null, false, false, true))
        assertFalse(SmsNotificationContent.acceptPackage("com.tencent.mm", "com.android.mms", false, true, false))
        assertFalse(SmsNotificationContent.acceptPackage("com.android.mms", "com.android.mms", false, false, true))
        assertFalse(SmsNotificationContent.acceptPackage("com.tencent.mm.evil", null, false, false, true))
        assertFalse(SmsNotificationContent.acceptPackage("com.tencent.mm", null, true, false, true))
        assertFalse(SmsNotificationContent.acceptPackage("com.other.chat", null, false, true, true))
    }

    @Test fun `wechat visible pickup text extracts while hidden notices do not`() {
        fun codes(text: String) = com.pickupcode.app.extractor.CodeExtractor.extract(
            listOf(com.pickupcode.app.ocr.OCREngine.TextLine(text, null, 1f)))
            .map { it.code }.toSet()
        assertEquals(setOf("7-3-4139"), codes("驿站助手:【圆通快递】凭7-3-4139到青禾村24排4号取尾号2642包裹。"))
        assertEquals(setOf("7922-0881"), codes("驿站助手: 取件码7922-0881，到青禾村24排菜鸟驿站7号柜18-15号格口取件"))
        assertTrue(codes("你收到了一条新消息").isEmpty())
        assertTrue(codes("[3条] 驿站助手: [图片]").isEmpty())
        assertTrue(com.pickupcode.app.extractor.CodeExtractor.isFinancialNoise("微信支付: 交易验证码618008"))
    }

    @Test fun `rule page uses production prefix and validator formats`() {
        val rules = com.pickupcode.app.extractor.CodeExtractor.builtinRuleInfos(null)
        assertEquals(rules.size, rules.map { it.id }.distinct().size)
        val prefix = rules.single { it.id == "PREFIXED_CODE" }
        assertTrue(prefix.label.contains("八位柜机码"))
        assertEquals("7922-0881", Regex(prefix.regex).find("取件码7922-0881")?.groupValues?.get(2))
        assertTrue(com.pickupcode.app.extractor.CodeValidator.validCodeFormatPatterns()
            .any { Regex(it).matches("7922-0881") })
    }
}
