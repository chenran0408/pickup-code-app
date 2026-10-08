package com.pickupcode.app.ui.miuix

import androidx.compose.ui.unit.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MenuPositionTest {
    private val position = MenuPosition(8)

    @Test fun `通常在按钮下方展开并靠右对齐`() {
        assertEquals(IntOffset(140, 156), position.calculatePosition(IntRect(292, 100, 340, 148),
            IntSize(360, 800), LayoutDirection.Ltr, IntSize(200, 180)))
    }

    @Test fun `底部按钮的菜单向上展开且不会被裁掉`() {
        assertEquals(IntOffset(140, 522), position.calculatePosition(IntRect(292, 710, 340, 758),
            IntSize(360, 800), LayoutDirection.Ltr, IntSize(200, 180)))
    }

    @Test fun `RTL 菜单靠左对齐并在窄窗口保留边距`() {
        assertEquals(IntOffset(20, 156), position.calculatePosition(IntRect(20, 100, 68, 148),
            IntSize(260, 400), LayoutDirection.Rtl, IntSize(180, 180)))
        assertEquals(IntOffset(8, 8), position.calculatePosition(IntRect(0, 0, 48, 48),
            IntSize(260, 180), LayoutDirection.Ltr, IntSize(244, 164)))
    }
}
