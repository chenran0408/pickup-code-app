package com.pickupcode.app.ui.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Existing dialog call sites mapped to Miuix's native overlay dialog. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") properties: DialogProperties = DialogProperties(),
    icon: @Composable (() -> Unit)? = null,
) {
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = true,
        modifier = modifier,
        onDismissRequest = onDismissRequest,
    ) {
        Column(Modifier.fillMaxWidth()) {
            icon?.invoke()
            title?.let {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides MiuixTheme.colorScheme.onSurface,
                ) {
                    androidx.compose.material3.ProvideTextStyle(MiuixTheme.textStyles.title4) { it() }
                }
                Spacer(Modifier.height(16.dp))
            }
            Box(Modifier.weight(1f, fill = false)) { text?.invoke() }
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/** App-specific sizing/positioning around Miuix's native list popup. */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }
    val maxWidth = minOf(320.dp, (LocalConfiguration.current.screenWidthDp.dp - 32.dp).coerceAtLeast(1.dp))
    top.yukonga.miuix.kmp.overlay.OverlayListPopup(
        show = expanded,
        popupModifier = modifier.widthIn(min = minOf(160.dp, maxWidth), max = maxWidth),
        popupPositionProvider = remember(margin) { MenuPosition(margin, 8.dp) },
        enableWindowDim = false,
        onDismissRequest = onDismissRequest,
        maxHeight = 420.dp,
    ) {
        CompositionLocalProvider(LocalDensity provides density) {
            top.yukonga.miuix.kmp.basic.Card(
                modifier = Modifier.widthIn(min = minOf(160.dp, maxWidth), max = maxWidth),
                cornerRadius = 16.dp,
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(8.dp), content = content)
            }
        }
    }
}

@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    TextButton(onClick, modifier.fillMaxWidth(), enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        leadingIcon?.invoke()
        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) { text() }
        trailingIcon?.invoke()
    }
}

