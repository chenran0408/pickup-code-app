package com.pickupcode.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

internal fun pickupMiuixColors(dark: Boolean): Colors =
    (if (dark) darkColorScheme() else lightColorScheme()).let { scheme ->
        val readable = pickupReadableColors(dark)
        val semantic = pickupSemanticColors(dark)
        scheme.copy(
            surface = readable.surface,
            background = readable.background,
            onSurface = readable.body,
            onSurfaceVariantSummary = readable.summary,
            secondaryContainer = readable.inputBackground,
            onSecondaryContainer = readable.inputLabel,
            tertiaryContainer = readable.emphasisBackground,
            onTertiaryContainer = readable.emphasis,
            error = semantic.error,
            onError = semantic.onError,
            errorContainer = semantic.errorContainer,
            onErrorContainer = semantic.onErrorContainer,
        )
    }

internal data class ReadableColors(
    val surface: Color,
    val background: Color,
    val body: Color,
    val summary: Color,
    val inputLabel: Color,
    val inputBackground: Color,
    val emphasis: Color,
    val emphasisBackground: Color,
)

// Keep semantic Miuix surfaces paired with readable text colors in both color modes.
internal fun pickupReadableColors(dark: Boolean): ReadableColors = if (dark) {
    ReadableColors(
        surface = Color(0xFF242424),
        background = Color.Black,
        body = Color(0xFFF2F2F2),
        summary = Color(0xFFB3B3B3),
        inputLabel = Color(0xFFBDBDBD),
        inputBackground = Color(0xFF434343),
        emphasis = Color(0xFF9CC3FF),
        emphasisBackground = Color(0xFF2B3B54),
    )
} else {
    ReadableColors(
        surface = Color.White,
        background = Color(0xFFF7F7F7),
        body = Color(0xFF1A1A1A),
        summary = Color(0xFF666666),
        inputLabel = Color(0xFF666666),
        inputBackground = Color(0xFFF0F0F0),
        emphasis = Color(0xFF235FB5),
        emphasisBackground = Color(0xFFEAF2FF),
    )
}

internal data class SemanticColors(
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

internal fun pickupSemanticColors(colors: Colors) = SemanticColors(
    error = colors.error,
    onError = colors.onError,
    errorContainer = colors.errorContainer,
    onErrorContainer = colors.onErrorContainer,
)

internal fun pickupSemanticColors(dark: Boolean): SemanticColors = if (dark) {
    SemanticColors(
        error = Color(0xFFF2AAA5),
        onError = Color(0xFF4B201E),
        errorContainer = Color(0xFF472C2B),
        onErrorContainer = Color(0xFFFFC5C1),
    )
} else {
    SemanticColors(
        error = Color(0xFFB83C35),
        onError = Color.White,
        errorContainer = Color(0xFFFBEAE9),
        onErrorContainer = Color(0xFF96342F),
    )
}

// 用 Miuix 的主题角色表达类型层级，减少与基础色板脱节的手工颜色。
val TypeFood: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val TypeParcel: Color
    @Composable get() = MaterialTheme.colorScheme.secondary
val TypeCoupon: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary
