package com.pickupcode.app.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DisplayFontScaleTest {
    @Test
    fun `大字模式至少放大三成且不缩小系统更大字号`() {
        assertEquals(1.3f, displayFontScale(1f, true))
        assertEquals(1.6f, displayFontScale(1.6f, true))
        assertEquals(1.15f, displayFontScale(1.15f, false))
    }
}
