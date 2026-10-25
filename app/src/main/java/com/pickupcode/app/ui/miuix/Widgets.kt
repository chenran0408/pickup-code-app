package com.pickupcode.app.ui.miuix

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.utils.PressFeedbackType

// 原有页面保留色彩角色与回调签名；可见控件由 miuix 绘制，避免逐页混用两套视觉样式。
private val LocalContent = top.yukonga.miuix.kmp.theme.LocalContentColor

@Composable
private fun controlHeight(): Dp = if (LocalDensity.current.fontScale >= 1.3f) 56.dp else 48.dp

@Composable
fun Text(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1, style: TextStyle = MiuixTheme.textStyles.main) {
    top.yukonga.miuix.kmp.basic.Text(text, modifier,
        color = if (color != Color.Unspecified) color else if (LocalContent.current != Color.Unspecified) LocalContent.current else MaterialTheme.colorScheme.onSurface,
        fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily, textAlign = textAlign,
        lineHeight = lineHeight, textDecoration = textDecoration, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, style = style)
}

@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified) = top.yukonga.miuix.kmp.basic.Icon(imageVector, contentDescription, modifier,
        if (tint != Color.Unspecified) tint else if (LocalContent.current != Color.Unspecified) LocalContent.current else MiuixTheme.colorScheme.onSurface)

@Composable
fun Icon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified) = top.yukonga.miuix.kmp.basic.Icon(painter, contentDescription, modifier,
        if (tint != Color.Unspecified) tint else if (LocalContent.current != Color.Unspecified) LocalContent.current else MiuixTheme.colorScheme.onSurface)

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    top.yukonga.miuix.kmp.basic.IconButton(onClick, modifier, enabled, backgroundColor = Color.Transparent,
        minWidth = controlHeight(), minHeight = controlHeight(), content = content)
}

@Composable
fun Surface(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(0.dp),
    color: Color = MaterialTheme.colorScheme.surface, contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp, shadowElevation: Dp = 0.dp, border: BorderStroke? = null, content: @Composable () -> Unit) {
    top.yukonga.miuix.kmp.basic.Surface(modifier, shape = shape, color = color, border = border,
        shadowElevation = shadowElevation) { CompositionLocalProvider(LocalContent provides contentColor, content = content) }
}

@Composable
fun Card(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(16.dp),
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation: CardElevation = CardDefaults.cardElevation(0.dp),
    border: BorderStroke? = null, content: @Composable ColumnScope.() -> Unit) {
    if (border != null) {
        Surface(modifier, shape, colors.containerColor, colors.contentColor, border = border) { Column(content = content) }
    } else {
        top.yukonga.miuix.kmp.basic.Card(
            modifier = modifier,
            cornerRadius = 16.dp,
            colors = top.yukonga.miuix.kmp.basic.CardColors(colors.containerColor, colors.contentColor),
            content = content,
        )
    }
}

@Composable
fun Card(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp), colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation: CardElevation = CardDefaults.cardElevation(0.dp), border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit) {
    if (border != null) {
        Card(modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick), shape, colors, elevation, border, content)
    } else {
        top.yukonga.miuix.kmp.basic.Card(
            modifier = modifier,
            cornerRadius = 16.dp,
            colors = top.yukonga.miuix.kmp.basic.CardColors(colors.containerColor, colors.contentColor),
            pressFeedbackType = PressFeedbackType.Sink,
            showIndication = true,
            onClick = if (enabled) onClick else null,
            content = content,
        )
    }
}

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp), colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null, contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit) {
    val background = if (colors.containerColor == Color.Transparent) MiuixTheme.colorScheme.secondaryVariant
        else colors.containerColor
    if (border != null) {
        top.yukonga.miuix.kmp.basic.Surface(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            color = background,
            contentColor = colors.contentColor,
            border = border,
        ) {
            ProvideTextStyle(MiuixTheme.textStyles.button) {
                Row(Modifier.defaultMinSize(minHeight = controlHeight()).padding(contentPadding),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content)
            }
        }
    } else {
        top.yukonga.miuix.kmp.basic.Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            minHeight = controlHeight(),
            colors = top.yukonga.miuix.kmp.basic.ButtonColors(
                color = background,
                disabledColor = colors.disabledContainerColor,
                contentColor = colors.contentColor,
                disabledContentColor = colors.disabledContentColor,
            ),
            insideMargin = contentPadding,
            content = content,
        )
    }
}

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    shape: Shape = RoundedCornerShape(12.dp), content: @Composable RowScope.() -> Unit) {
    top.yukonga.miuix.kmp.basic.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = controlHeight(),
        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
            color = Color.Transparent,
            disabledColor = colors.disabledContainerColor,
            contentColor = colors.contentColor,
            disabledContentColor = colors.disabledContentColor,
        ),
        insideMargin = contentPadding,
        content = content,
    )
}

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp), colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = null, contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit) = Button(onClick, modifier, enabled, shape, colors, border, contentPadding, content)

@Composable
fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp), colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp), content: @Composable RowScope.() -> Unit) =
    Button(onClick, modifier, enabled, shape, colors, contentPadding = contentPadding, content = content)

@Composable
fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(), shape: Shape = RoundedCornerShape(12.dp), border: BorderStroke? = null) {
    val background = if (selected) MaterialTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryVariant
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Button(onClick, modifier.semantics { this.selected = selected; role = Role.Checkbox }, enabled, shape,
        ButtonDefaults.buttonColors(containerColor = background, contentColor = foreground), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        leadingIcon?.invoke(); if (leadingIcon != null) Spacer(Modifier.width(6.dp)); label()
        trailingIcon?.invoke()
    }
}

@Composable
fun AssistChip(onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, colors: ChipColors = AssistChipDefaults.assistChipColors(
        containerColor = MiuixTheme.colorScheme.secondaryVariant, labelColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
    Button(onClick, modifier, enabled, colors = ButtonDefaults.buttonColors(containerColor = colors.containerColor,
        contentColor = colors.labelColor), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { label() }
}

@Composable
fun Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) =
    top.yukonga.miuix.kmp.basic.Switch(checked, onCheckedChange, modifier, enabled = enabled)

@Composable
fun Checkbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) =
    top.yukonga.miuix.kmp.basic.Checkbox(
        state = ToggleableState(checked),
        onClick = if (enabled && onCheckedChange != null) ({ onCheckedChange(!checked) }) else null,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        enabled = enabled,
    )

@Composable
fun OutlinedTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    labelText: String = "", placeholder: @Composable (() -> Unit)? = null, enabled: Boolean = true, readOnly: Boolean = false,
    singleLine: Boolean = false, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, minLines: Int = 1,
    isError: Boolean = false, supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None) {
    Column(modifier) {
        top.yukonga.miuix.kmp.basic.TextField(value, onValueChange, Modifier.fillMaxWidth(), label = labelText,
            colors = top.yukonga.miuix.kmp.basic.TextFieldDefaults.textFieldColors(
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                labelColor = if (isError) MaterialTheme.colorScheme.error else MiuixTheme.colorScheme.onSecondaryContainer,
            ),
            enabled = enabled, readOnly = readOnly, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, visualTransformation = visualTransformation,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon)
        // 原来的长输入提示放在字段下方，避免空值时与 miuix 浮动标签重叠。
        if (value.isEmpty() && placeholder != null) Box(Modifier.padding(start = 12.dp, top = 4.dp)) { placeholder() }
        if (supportingText != null) Box(Modifier.padding(start = 12.dp, top = 4.dp)) { supportingText() }
    }
}

@Composable
fun TopAppBar(title: String, modifier: Modifier = Modifier, navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}) {
    top.yukonga.miuix.kmp.basic.SmallTopAppBar(title, modifier.statusBarsPadding(),
        navigationIcon = navigationIcon, actions = actions)
}

@Composable
fun Scaffold(modifier: Modifier = Modifier, topBar: @Composable () -> Unit = {}, bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {}, snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MiuixTheme.colorScheme.background, content: @Composable (PaddingValues) -> Unit) =
    top.yukonga.miuix.kmp.basic.Scaffold(modifier, topBar, bottomBar, floatingActionButton,
        snackbarHost = snackbarHost, containerColor = containerColor, contentWindowInsets = WindowInsets.safeDrawing, content = content)

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = MiuixTheme.colorScheme.dividerLine) =
    Box(modifier.fillMaxWidth().height(thickness).background(color))

@Composable
fun VerticalDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = MiuixTheme.colorScheme.dividerLine) =
    Box(modifier.width(thickness).fillMaxHeight().background(color))

// The Miuix 0.9.x progress indicators provide their own track and motion semantics.
@Composable
fun LinearProgressIndicator(progress: () -> Float, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary, trackColor: Color = MiuixTheme.colorScheme.secondaryVariant) {
    top.yukonga.miuix.kmp.basic.LinearProgressIndicator(
        modifier = modifier,
        progress = progress().coerceIn(0f, 1f),
        colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(
            foregroundColor = color,
            backgroundColor = trackColor,
        ),
    )
}

@Composable
fun LinearProgressIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    top.yukonga.miuix.kmp.basic.LinearProgressIndicator(
        modifier = modifier,
        colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
    )
}

@Composable
fun CircularProgressIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, strokeWidth: Dp = 3.dp) {
    top.yukonga.miuix.kmp.basic.CircularProgressIndicator(
        modifier = modifier,
        colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
        strokeWidth = strokeWidth,
    )
}
