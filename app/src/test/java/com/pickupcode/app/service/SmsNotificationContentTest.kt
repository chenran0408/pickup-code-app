package com.pickupcode.app.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SmsNotificationContentTest {
    @Test fun `ordinary chats tracking notices and OTP do not provide pickup context`() {
        assertFalse(SmsNotificationContent.hasPickupContext("明天见，会议编号607284"))
        assertFalse(SmsNotificationContent.hasPickupContext("登录验证码607284，请勿泄露"))
        assertFalse(SmsNotificationContent.hasPickupContext("运单号435228469827702，物流正在运输"))
        assertTrue(SmsNotificationContent.hasPickupContext("凭7-3-5268到青禾村24排4号取件"))
        assertTrue(SmsNotificationContent.hasPickupContext("取餐码A12，请到前台取餐"))
    }
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
    @Test fun `shopping packages require their own opt in and exact package`() {
        val sources = listOf(SmsNotificationContent.Source.TAOBAO,
            SmsNotificationContent.Source.PINDUODUO, SmsNotificationContent.Source.JD)
        for (enabled in sources) {
            fun accepts(pkg: String, summary: Boolean = false) = SmsNotificationContent.acceptPackage(
                pkg, "com.android.mms", summary, false, false,
                enabled == SmsNotificationContent.Source.TAOBAO,
                enabled == SmsNotificationContent.Source.PINDUODUO,
                enabled == SmsNotificationContent.Source.JD)
            assertFalse(SmsNotificationContent.acceptPackage(enabled.packageName, null, false))
            assertTrue(accepts(enabled.packageName))
            assertEquals(enabled, SmsNotificationContent.sourceFor(enabled.packageName, null, false))
            assertFalse(accepts(enabled.packageName + ".fake"))
            assertFalse(accepts(enabled.packageName, true))
            assertFalse(accepts("com.android.mms"))
            assertFalse(accepts("com.tencent.mm"))
            for (other in sources.filter { it != enabled }) assertFalse(accepts(other.packageName))
        }
        assertFalse(SmsNotificationContent.acceptPackage("com.bank", null, false, true, true, true, true, true))
        assertFalse(SmsNotificationContent.acceptPackage("com.pickupcode.app", null, false, true, true, true, true, true))
    }

    @Test fun `shopping title and visible body provide pickup context without accepting OTP or logistics numbers`() {
        for (source in listOf(SmsNotificationContent.Source.TAOBAO,
            SmsNotificationContent.Source.PINDUODUO, SmsNotificationContent.Source.JD)) {
            fun text(title: String, body: String) = SmsNotificationContent.recognitionText(source, title, body)
            fun codes(title: String, body: String): Set<String> {
                val text = text(title, body)
                if (!SmsNotificationContent.hasPickupContext(text)) return emptySet()
                return com.pickupcode.app.extractor.CodeExtractor.extract(text.lines().map {
                    com.pickupcode.app.ocr.OCREngine.TextLine(it, null, 1f)
                }).map { it.code }.toSet()
            }
            assertEquals(setOf("7-3-5268"), codes("包裹到站", "凭7-3-5268到青禾村24排4号取件"))
            assertEquals(setOf("7-3-5268"), codes("取件码", "7-3-5268"))
            assertTrue(codes("取件提醒", "").isEmpty())
            assertTrue(codes("物流更新", "运单号435228469827702，正在运输").isEmpty())
            assertTrue(codes("登录验证", "验证码618008，请勿泄露").isEmpty())
            assertTrue(codes("取件提醒", "登录验证码618008，请勿泄露").isEmpty())
            assertEquals("", text("取件码7-3-5268", ""))
        }
    }

    @Test fun `shopping title is bounded and chat titles are excluded`() {
        assertEquals(20000, SmsNotificationContent.recognitionText(SmsNotificationContent.Source.JD,
            "a".repeat(1000), "b".repeat(25000)).length)
        assertEquals("正文", SmsNotificationContent.recognitionText(SmsNotificationContent.Source.WECHAT,
            "取件码7-3-5268", "正文"))
        assertEquals("正文", SmsNotificationContent.recognitionText(SmsNotificationContent.Source.SMS,
            "取件码7-3-5268", "正文"))
    }

}
