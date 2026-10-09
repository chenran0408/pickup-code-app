package com.pickupcode.app.ui.theme

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ThemeContrastTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `正文提示与状态文字在深浅色中保持可读`(dark: Boolean) {
        val colors = pickupReadableColors(dark)
        val semantic = pickupSemanticColors(dark)
        val pairs = listOf(
            "正文" to (colors.body to colors.surface),
            "卡片摘要" to (colors.summary to colors.surface),
            "页面摘要" to (colors.summary to colors.background),
            "输入提示" to (colors.inputLabel to colors.inputBackground),
            "柔和强调" to (colors.emphasis to colors.emphasisBackground),
            "错误提示" to (semantic.error to colors.surface),
            "错误状态" to (semantic.onErrorContainer to semantic.errorContainer),
        )
        for ((name, pair) in pairs) {
            val background = pair.second
            val foreground = pair.first.compositeOver(background)
            val high = maxOf(foreground.luminance(), background.luminance())
            val low = minOf(foreground.luminance(), background.luminance())
            val ratio = (high + 0.05f) / (low + 0.05f)
            assertTrue(ratio >= 4.5f, "$name dark=$dark contrast=$ratio")
        }
    }
}
