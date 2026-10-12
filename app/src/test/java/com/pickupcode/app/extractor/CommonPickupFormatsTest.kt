package com.pickupcode.app.extractor

import com.pickupcode.app.ocr.OCREngine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test

class CommonPickupFormatsTest {
    @ParameterizedTest
    @ValueSource(strings = ["7-3-5268", "A3-2-5268", "A3-2-4-68", "A-2-568", "H-52786",
        "A3-52786", "52-6789", "5278", "52786", "675839", "67583924", "5278-6934",
        "7_3_5268", "52_6789", "5278_6934"])
    fun common_codes_after_pickup_prefix(code: String) {
        val result = CodeExtractor.extract(listOf(OCREngine.TextLine("【菜鸟驿站】取件码：$code，请到青禾站取件。", null, null)))
        assertEquals(listOf(code), result.map { it.code })
        assertEquals(CodeExtractor.CodeType.pickup_parcel, result.single().type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["来取", "扫码开门自助取件", "前往青禾站取件", "领取", "取货"])
    fun zone_code_after_ping(codeSuffix: String) {
        val result = CodeExtractor.extract(listOf(OCREngine.TextLine("【顺丰】包裹已到青禾站，请凭A3-52786$codeSuffix。", null, null)))
        assertEquals(listOf("A3-52786"), result.map { it.code })
    }

    @ParameterizedTest
    @ValueSource(strings = ["提货码", "取件密码"])
    fun pickup_label_aliases(label: String) {
        val result = CodeExtractor.extract(listOf(OCREngine.TextLine("【顺丰】$label 为675839，请及时取件。", null, null)))
        assertEquals(listOf("675839"), result.map { it.code })
        assertEquals(CodeExtractor.CodeType.pickup_parcel, result.single().type)
    }

    @Test fun separators_do_not_bypass_repeated_digit_noise() {
        assertFalse(CodeValidator.isValidPickupCode("00_00_000"))
        assertFalse(CodeValidator.isValidPickupCode("11_11_111"))
        assertTrue(CodeExtractor.extract(listOf(OCREngine.TextLine("【通知】电话17812345678，运单号SF123456789012，18:00下班。", null, null))).isEmpty())
    }
}
