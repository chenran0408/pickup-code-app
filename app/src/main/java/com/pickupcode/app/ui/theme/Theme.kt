package com.pickupcode.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickupcode.app.preferences.AppPreferences
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SmoothRoundedCornerShape

@Composable
fun PickupCodeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val appearance by remember(context) { AppPreferences.observeAppearance(context) }
        .collectAsStateWithLifecycle(initialValue = AppPreferences.Appearance())
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance.mode) { "dark" -> true; "light" -> false; else -> systemDark }
    val systemDensity = LocalDensity.current
    val density = remember(systemDensity, appearance.largeText) {
        Density(systemDensity.density, displayFontScale(systemDensity.fontScale, appearance.largeText))
    }
    val colors = remember(dark) { pickupMiuixColors(dark) }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalDensity provides density) {
        MiuixTheme(colors = colors) {
            // Material 的状态/色彩类型仍供旧业务页面使用，色彩与字阶统一映射到 miuix 主题。
            val mapped = remember(colors, dark) {
                val tokens = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
                val semantic = pickupSemanticColors(dark)
                tokens.copy(
                primary = colors.primary, onPrimary = colors.onPrimary,
                // Material 容器角色用 miuix 的柔和强调底色；实心主按钮仍使用 primary。
                primaryContainer = colors.tertiaryContainer, onPrimaryContainer = colors.onTertiaryContainer,
                secondary = colors.secondaryVariant, onSecondary = colors.onSecondaryVariant,
                secondaryContainer = colors.secondaryContainer, onSecondaryContainer = colors.onSecondaryVariant,
                tertiary = colors.primary, onTertiary = colors.onPrimary,
                tertiaryContainer = colors.tertiaryContainer, onTertiaryContainer = colors.onTertiaryContainer,
                error = semantic.error, onError = semantic.onError,
                errorContainer = semantic.errorContainer, onErrorContainer = semantic.onErrorContainer,
                background = colors.background, onBackground = colors.onBackground,
                surface = colors.surface, onSurface = colors.onSurface,
                surfaceVariant = colors.surfaceVariant, onSurfaceVariant = colors.onSurfaceVariantSummary,
                outline = colors.outline, outlineVariant = colors.dividerLine,
                surfaceContainer = colors.surfaceContainer, surfaceContainerHigh = colors.surfaceContainerHigh,
                surfaceContainerHighest = colors.surfaceContainerHighest, surfaceContainerLow = colors.surfaceContainer,
                surfaceContainerLowest = colors.surface, surfaceTint = colors.primary)
            }
            val styles = MiuixTheme.textStyles
            MaterialTheme(colorScheme = mapped,
                typography = Typography(bodyLarge = styles.main, bodyMedium = styles.body1, bodySmall = styles.footnote1,
                    titleLarge = styles.title4, titleMedium = styles.subtitle, titleSmall = styles.headline2,
                    headlineLarge = styles.title1, headlineMedium = styles.title2, headlineSmall = styles.title3,
                    labelLarge = styles.button, labelMedium = styles.footnote1, labelSmall = styles.footnote2),
                shapes = Shapes(small = SmoothRoundedCornerShape(12.dp), medium = SmoothRoundedCornerShape(20.dp),
                    large = SmoothRoundedCornerShape(20.dp), extraLarge = SmoothRoundedCornerShape(28.dp))) {
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.onSurface, content = content)
            }
        }
    }
}

internal fun displayFontScale(systemScale: Float, largeText: Boolean): Float =
    if (largeText) maxOf(systemScale, 1.3f) else systemScale
