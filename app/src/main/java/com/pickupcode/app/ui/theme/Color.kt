package com.pickupcode.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

// 保留 miuix 的蓝色、层次和圆角语言，摘要、输入提示及柔和强调文字按两种背景配对。
internal data class ReadableColors(val surface: Color, val background: Color, val body: Color,
    val summary: Color, val inputLabel: Color, val inputBackground: Color, val emphasis: Color, val emphasisBackground: Color)

internal fun pickupReadableColors(dark: Boolean) = if (dark)
    ReadableColors(Color(0xFF242424), Color.Black, Color(0xFFF2F2F2), Color(0xFFB3B3B3),
        Color(0xFFBDBDBD), Color(0xFF434343), Color(0xFF9CC3FF), Color(0xFF2B3B54))
else ReadableColors(Color.White, Color(0xFFF7F7F7), Color(0xFF1A1A1A), Color(0xFF666666),
    Color(0xFF666666), Color(0xFFF0F0F0), Color(0xFF235FB5), Color(0xFFEAF2FF))

internal fun pickupMiuixColors(dark: Boolean): Colors {
    val readable = pickupReadableColors(dark)
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        surface = readable.surface, background = readable.background, onSurface = readable.body,
        onSurfaceVariantSummary = readable.summary, onSecondaryContainer = readable.inputLabel,
        secondaryContainer = readable.inputBackground,
        onTertiaryContainer = readable.emphasis, tertiaryContainer = readable.emphasisBackground)
}

internal data class SemanticColors(val error: Color, val onError: Color, val errorContainer: Color, val onErrorContainer: Color)
internal fun pickupSemanticColors(dark: Boolean) = if (dark)
    SemanticColors(Color(0xFFF2AAA5), Color(0xFF4B201E), Color(0xFF472C2B), Color(0xFFFFC5C1))
else SemanticColors(Color(0xFFB83C35), Color.White, Color(0xFFFBEAE9), Color(0xFF96342F))

// 类型识别色（左侧竖条 + badge）
val TypeFood: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val TypeParcel: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        Color(0xFFC0A8F0) else Color(0xFF7553B8)
val TypeCoupon: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        Color(0xFFE9BE70) else Color(0xFF986000)
