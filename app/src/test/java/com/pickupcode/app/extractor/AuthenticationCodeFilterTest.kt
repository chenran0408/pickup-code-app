package com.pickupcode.app.extractor

import com.pickupcode.app.ocr.OCREngine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuthenticationCodeFilterTest {
    private fun extract(text: String) = CodeExtractor.extract(text.lines().map { OCREngine.TextLine(it, null, 1f) })
        .map { it.code }.toSet()

    @Test fun `Tencent login verification cannot become a pickup record`() {
        val text = "【腾讯科技】你正在进行[QQ 27*******2登录验证]，验证码607284。提供给他人会导致QQ被盗，若非本人操作，请修改密码。"
        assertTrue(AuthenticationCodeFilter.isAuthenticationOnly(text))
        assertTrue(AuthenticationCodeFilter.reject("607284", text))
        assertTrue(extract(text).isEmpty())
    }

    @Test fun `authentication formats and OCR line breaks are rejected`() {
        listOf("验证码：607284，5分钟内有效", "本次校验码为739205，请勿告知他人", "动态密码：398517",
            "一次性口令618209", "Your verification code is 607284", "OTP: 607284",
            "验证码\n６０７２８４\n请勿泄露", "【快递平台】登录验证码607284，请勿告知他人").forEach {
            assertTrue(extract(it).isEmpty(), it)
        }
    }

    @Test fun `ordinary pickup notifications still work`() {
        assertEquals(setOf("739205"), extract("【兔喜生活】请凭739205至青禾村24排菜鸟驿站中通1号柜取件"))
        assertEquals(setOf("7922-0881"), extract("取件码7922-0881，到青禾村24排菜鸟驿站取件"))
        assertEquals(setOf("7-3-5268"), extract("【圆通快递】凭7-3-5268到青禾村24排4号取包裹"))
    }

    @Test fun `mixed notification keeps pickup and removes verification number`() {
        val text = "【腾讯科技】验证码607284，请勿泄露。\n【圆通快递】凭7-3-5268到青禾村24排4号取件。"
        assertEquals(setOf("7-3-5268"), extract(text))
        assertTrue(AuthenticationCodeFilter.reject("607284", text))
        assertFalse(AuthenticationCodeFilter.reject("7-3-5268", text))
        val sameLine = "取件码739205，登录验证码607284，请勿泄露。"
        assertEquals(setOf("739205"), extract(sameLine))
        assertTrue(AuthenticationCodeFilter.reject("607284", sameLine))
        assertFalse(AuthenticationCodeFilter.reject("739205", sameLine))
    }

    @Test fun `number before verification label is rejected and AI cannot bypass guard`() {
        val text = "607284是您的验证码。取件码739205。"
        assertTrue(AuthenticationCodeFilter.reject("607284", text))
        assertFalse(AuthenticationCodeFilter.reject("739205", text))
        assertEquals(setOf("739205"), extract(text))
        assertTrue(AuthenticationCodeFilter.reject("739205", "验证码\n739205\n请勿泄露"))
    }
}
