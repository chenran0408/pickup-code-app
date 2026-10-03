package com.pickupcode.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickupcode.app.preferences.AppPreferences

// 清爽蓝色系（浅色）
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    surfaceTint = Primary,
    outline = Color(0xFF9CA3AF),
    outlineVariant = Color(0xFFD1D5DB),
    surfaceContainerLowest = Surface,
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainer = Background,
    surfaceContainerHigh = Color(0xFFEFF2F6),
    surfaceContainerHighest = Color(0xFFE5E7EB),
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError,
    background = Background,
    onBackground = OnSurface
)

// 灰蓝调深色（与 7E9EB5 主色统一）
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFF93C5FD),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF170F29),
    secondaryContainer = Color(0xFF3B2A63),
    onSecondaryContainer = Color(0xFFE9E4FC),
    surfaceTint = Color(0xFF60A5FA),
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFF374151),
    surfaceContainerLowest = Color(0xFF0B1220),
    surfaceContainerLow = Color(0xFF111C2E),
    surfaceContainer = Color(0xFF1F2937),
    surfaceContainerHigh = Color(0xFF263244),
    surfaceContainerHighest = Color(0xFF374151),
    surface = Color(0xFF1F2937),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = Color(0xFF9CA3AF),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF3F0D0D),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE5E7EB)
)

// 按控件角色统一圆角：按钮/筛选 12dp，卡片 16dp，对话框 24dp。
private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PickupCodeTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val mode by androidx.compose.runtime.remember(context) { AppPreferences.observeTheme(context) }
        .collectAsStateWithLifecycle(initialValue = "system")

    val systemDark = isSystemInDarkTheme()
    val isDark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val act = view.context as? Activity ?: return@SideEffect
            val window = act.window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        content = content
    )
}
