package com.pickupcode.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

internal fun pickupMiuixColors(dark: Boolean): Colors =
    if (dark) darkColorScheme() else lightColorScheme()

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

// 用 Miuix 的主题角色表达类型层级，减少与基础色板脱节的手工颜色。
val TypeFood: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val TypeParcel: Color
    @Composable get() = MaterialTheme.colorScheme.secondary
val TypeCoupon: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary
