package com.pickupcode.app.extractor

import com.pickupcode.app.ocr.OCREngine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CodeContextTest {
    private fun lines(vararg texts: String) = texts.map { OCREngine.TextLine(it, null, 1f) }

    @Test fun `each card keeps its own cabinet and deadline`() {
        val input = lines("取件码3-7-4162", "长兴路2号柜 请于今天取件", "分隔", "取件码9-4-1526", "长青街8号柜 请于明天取件")
        val codes = listOf("3-7-4162", "9-4-1526")
        val first = CodeContext.linesForCode(input, codes[0], codes).joinToString(" ") { it.text }
        val second = CodeContext.linesForCode(input, codes[1], codes).joinToString(" ") { it.text }
        assertTrue(first.contains("2号柜"))
        assertFalse(first.contains("8号柜"))
        assertTrue(second.contains("8号柜"))
        assertFalse(second.contains("2号柜"))
        assertTrue(first.contains("今天"))
        assertFalse(first.contains("明天"))
    }

    @Test fun `ambiguous shared row is omitted instead of assigning to both cards`() {
        val input = lines("取件码3-7-4162", "地址不确定", "取件码9-4-1526")
        val codes = listOf("3-7-4162", "9-4-1526")
        assertFalse(CodeContext.linesForCode(input, codes[0], codes).any { it.text == "地址不确定" })
        assertFalse(CodeContext.linesForCode(input, codes[1], codes).any { it.text == "地址不确定" })
    }

    @Test fun `AI only code missing from OCR cannot borrow another card`() {
        val input = lines("取件码3-7-4162", "长兴路2号柜")
        assertTrue(CodeContext.linesForCode(input, "9-4-1526", listOf("3-7-4162", "9-4-1526")).isEmpty())
    }

    @Test fun `single code keeps entire available context`() {
        val input = lines("标题", "取件码3-7-4162", "地址长兴路")
        assertEquals(input, CodeContext.linesForCode(input, "3-7-4162", listOf("3-7-4162")))
    }
}
