package com.pickupcode.app.feedback

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeedbackSanitizerTest {
    @Test fun `masking preserves widths separators and repeated values without reversible digit shift`() {
        val input = "取件码7-3-5268，重复5268，电话13800139000，全角８０２４"
        val output = FeedbackSanitizer.maskNumbers(input)
        assertEquals(input.length, output.length)
        val old = Regex("[0-9０-９]+").findAll(input).map { it.value }.toList()
        val fresh = Regex("[0-9０-９]+").findAll(output).map { it.value }.toList()
        assertEquals(old.map { it.length }, fresh.map { it.length })
        old.zip(fresh).forEach { (a, b) -> assertNotEquals(a, b) }
        assertEquals(fresh[2], fresh[3])
        assertTrue(output.contains("取件码")); assertEquals(input.count { it == '-' }, output.count { it == '-' })
    }
}
