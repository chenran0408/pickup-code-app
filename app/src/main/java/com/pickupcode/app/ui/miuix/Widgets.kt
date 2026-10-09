@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pickupcode.app.ui.miuix

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SmoothRoundedCornerShape

// 原有页面保留色彩角色与回调签名；可见控件由 miuix 绘制，避免逐页混用两套视觉样式。
private val LocalContent = LocalContentColor

@Composable
private fun controlHeight(): Dp = if (LocalDensity.current.fontScale >= 1.3f) 56.dp else 48.dp

@Composable
fun Text(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1, style: TextStyle = LocalTextStyle.current) {
    top.yukonga.miuix.kmp.basic.Text(text, modifier,
        color = if (color != Color.Unspecified) color else if (LocalContent.current != Color.Unspecified) LocalContent.current else MaterialTheme.colorScheme.onSurface,
        fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily, textAlign = textAlign,
        lineHeight = lineHeight, textDecoration = textDecoration, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, style = style)
}

@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified) = top.yukonga.miuix.kmp.basic.Icon(imageVector, contentDescription, modifier,
        if (tint != Color.Unspecified) tint else if (LocalContent.current != Color.Unspecified) LocalContent.current else MaterialTheme.colorScheme.onSurface)

@Composable
fun Icon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified) = top.yukonga.miuix.kmp.basic.Icon(painter, contentDescription, modifier,
        if (tint != Color.Unspecified) tint else if (LocalContent.current != Color.Unspecified) LocalContent.current else MaterialTheme.colorScheme.onSurface)

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    top.yukonga.miuix.kmp.basic.IconButton(onClick, modifier, enabled, backgroundColor = Color.Transparent,
        minWidth = controlHeight(), minHeight = controlHeight(), content = content)
}

@Composable
fun Surface(modifier: Modifier = Modifier, shape: Shape = SmoothRoundedCornerShape(0.dp),
    color: Color = MaterialTheme.colorScheme.surface, contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp, shadowElevation: Dp = 0.dp, border: BorderStroke? = null, content: @Composable () -> Unit) {
    top.yukonga.miuix.kmp.basic.Surface(modifier, shape = shape, color = color, border = border,
        shadowElevation = shadowElevation.value) { CompositionLocalProvider(LocalContent provides contentColor, content = content) }
}

@Composable
fun Card(modifier: Modifier = Modifier, shape: Shape = SmoothRoundedCornerShape(20.dp),
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation: CardElevation = CardDefaults.cardElevation(0.dp),
    border: BorderStroke? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape, colors.containerColor, colors.contentColor, border = border) { Column(content = content) }
}

@Composable
fun Card(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = SmoothRoundedCornerShape(20.dp), colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation: CardElevation = CardDefaults.cardElevation(0.dp), border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit) = Card(modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick), shape, colors, elevation, border, content)

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = SmoothRoundedCornerShape(16.dp), colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null, contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit) {
    val background = (if (enabled) colors.containerColor else colors.disabledContainerColor)
        .let { if (it == Color.Transparent) MiuixTheme.colorScheme.secondaryVariant else it }
    top.yukonga.miuix.kmp.basic.Surface(onClick, modifier, enabled, shape, background, border) {
        CompositionLocalProvider(LocalContent provides if (enabled) colors.contentColor else colors.disabledContentColor) {
            ProvideTextStyle(MiuixTheme.textStyles.button) {
                Row(Modifier.defaultMinSize(minHeight = controlHeight()).padding(contentPadding),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content)
            }
        }
    }
}

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    shape: Shape = SmoothRoundedCornerShape(12.dp), content: @Composable RowScope.() -> Unit) {
    top.yukonga.miuix.kmp.basic.Surface(onClick, modifier, enabled, shape, Color.Transparent) {
        CompositionLocalProvider(LocalContent provides if (enabled) colors.contentColor else colors.disabledContentColor) {
            ProvideTextStyle(MiuixTheme.textStyles.button) {
                Row(Modifier.defaultMinSize(minHeight = controlHeight()).padding(contentPadding), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center, content = content)
            }
        }
    }
}

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = SmoothRoundedCornerShape(16.dp), colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = null, contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit) = Button(onClick, modifier, enabled, shape, colors, border, contentPadding, content)

@Composable
fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = SmoothRoundedCornerShape(16.dp), colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp), content: @Composable RowScope.() -> Unit) =
    Button(onClick, modifier, enabled, shape, colors, contentPadding = contentPadding, content = content)

@Composable
fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(), shape: Shape = SmoothRoundedCornerShape(12.dp), border: BorderStroke? = null) {
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
    top.yukonga.miuix.kmp.basic.Checkbox(checked, onCheckedChange, modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), enabled = enabled)

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
            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
            labelColor = if (isError) MaterialTheme.colorScheme.error else MiuixTheme.colorScheme.onSecondaryContainer,
            enabled = enabled, readOnly = readOnly, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, visualTransformation = visualTransformation,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon)
        // 原来的长输入提示放在字段下方，避免空值时与 miuix 浮动标签重叠。
        if (value.isEmpty() && placeholder != null) Box(Modifier.padding(start = 12.dp, top = 4.dp)) { placeholder() }
        if (supportingText != null) Box(Modifier.padding(start = 12.dp, top = 4.dp)) { supportingText() }
    }
}

@Composable
fun Slider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null, valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true) {
    val finished by rememberUpdatedState(onValueChangeFinished)
    val change by rememberUpdatedState(onValueChange)
    top.yukonga.miuix.kmp.basic.Slider(value, onValueChange,
        modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
            setProgress { change(it.coerceIn(valueRange)); finished?.invoke(); true }
        }.pointerInput(enabled) {
            if (enabled) awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do { val event = awaitPointerEvent(PointerEventPass.Final) } while (event.changes.any { it.pressed })
                finished?.invoke()
            }
        }, enabled, minValue = valueRange.start, maxValue = valueRange.endInclusive)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null, text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(), icon: @Composable (() -> Unit)? = null) {
    val density = LocalDensity.current
    Dialog(onDismissRequest, properties) {
        // 独立窗口会提供系统字号，重新传递应用的大字设置，避免弹窗字号回退。
        CompositionLocalProvider(LocalDensity provides density) {
        top.yukonga.miuix.kmp.basic.Card(modifier.fillMaxWidth(), cornerRadius = 28.dp, insideMargin = PaddingValues(24.dp)) {
            icon?.invoke()
            title?.let {
                CompositionLocalProvider(LocalContent provides MaterialTheme.colorScheme.onSurface) {
                    ProvideTextStyle(MiuixTheme.textStyles.title4) { it() }
                }
                Spacer(Modifier.height(16.dp))
            }
            Box(Modifier.weight(1f, fill = false)) { text?.invoke() }
            Spacer(Modifier.height(16.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dismissButton?.invoke(); confirmButton()
            }
        }
        }
    }
}

@Composable
fun TopAppBar(title: String, modifier: Modifier = Modifier, navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}, colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors()) {
    top.yukonga.miuix.kmp.basic.SmallTopAppBar(title, modifier.statusBarsPadding(),
        navigationIcon = navigationIcon, actions = actions, horizontalPadding = 12.dp)
}

@Composable
fun Scaffold(modifier: Modifier = Modifier, topBar: @Composable () -> Unit = {}, bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {}, snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MiuixTheme.colorScheme.background, content: @Composable (PaddingValues) -> Unit) =
    top.yukonga.miuix.kmp.basic.Scaffold(modifier, topBar, bottomBar, floatingActionButton,
        snackbarHost = snackbarHost, containerColor = containerColor, contentWindowInsets = WindowInsets.safeDrawing, content = content)

@Composable
fun ExtendedFloatingActionButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    shape: Shape = SmoothRoundedCornerShape(16.dp), containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary, content: @Composable RowScope.() -> Unit) =
    Button(onClick, modifier, shape = shape, colors = ButtonDefaults.buttonColors(containerColor, contentColor),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), content = content)

@Composable
fun FloatingActionButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    ExtendedFloatingActionButton(onClick, modifier) { content() }

@Composable
fun DropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val maxWidth = minOf(320.dp, (LocalConfiguration.current.screenWidthDp.dp - 32.dp).coerceAtLeast(1.dp))
    if (expanded) Popup(popupPositionProvider = remember(margin) { MenuPosition(margin) },
        onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        CompositionLocalProvider(LocalDensity provides density) {
        top.yukonga.miuix.kmp.basic.Card(modifier.widthIn(min = minOf(160.dp, maxWidth), max = maxWidth), cornerRadius = 20.dp) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(8.dp), content = content)
        }
        }
    }
}

@Composable
fun DropdownMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null) {
    TextButton(onClick, modifier.fillMaxWidth(), enabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        leadingIcon?.invoke(); Box(Modifier.weight(1f).padding(horizontal = 8.dp)) { text() }; trailingIcon?.invoke()
    }
}

@Composable
fun ModalDrawerSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val width = minOf(if (LocalDensity.current.fontScale >= 1.3f) 360.dp else 320.dp,
        (LocalConfiguration.current.screenWidthDp.dp - 24.dp).coerceAtLeast(1.dp))
    Surface(modifier.width(width).fillMaxHeight(), shape = SmoothRoundedCornerShape(24.dp), color = MiuixTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding(), content = content)
    }
}

@Composable
fun NavigationDrawerItem(label: @Composable () -> Unit, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier.fillMaxWidth().padding(vertical = 2.dp), colors = ButtonDefaults.buttonColors(
        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)) {
        Box(Modifier.fillMaxWidth()) { label() }
    }
}

@Composable
fun SingleChoiceSegmentedButtonRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)

@Composable
fun RowScope.SegmentedButton(selected: Boolean, onClick: () -> Unit, shape: Shape,
    colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(), label: @Composable () -> Unit) {
    FilterChip(selected, onClick, label, Modifier.weight(1f), shape = SmoothRoundedCornerShape(12.dp))
}

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = MiuixTheme.colorScheme.dividerLine) =
    Box(modifier.fillMaxWidth().height(thickness).background(color))

@Composable
fun VerticalDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = MiuixTheme.colorScheme.dividerLine) =
    Box(modifier.width(thickness).fillMaxHeight().background(color))

@Composable
fun SnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    androidx.compose.material3.SnackbarHost(hostState, modifier) { data ->
        top.yukonga.miuix.kmp.basic.Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(data.visuals.message, Modifier.weight(1f))
                data.visuals.actionLabel?.let { TextButton(onClick = { data.performAction() }) { Text(it) } }
                if (data.visuals.withDismissAction) TextButton(onClick = { data.dismiss() }) { Text("关闭") }
            }
        }
    }
}

// 此版本 miuix 没有进度控件，用统一色彩与圆头线条补齐，保持识别反馈。
@Composable
fun LinearProgressIndicator(progress: () -> Float, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary, trackColor: Color = MiuixTheme.colorScheme.secondaryVariant) {
    Canvas(modifier.fillMaxWidth().height(4.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f) }) {
        drawLine(trackColor, androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width * progress().coerceIn(0f, 1f), size.height / 2), size.height, StrokeCap.Round)
    }
}

@Composable
fun LinearProgressIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "loading")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "progress")
    LinearProgressIndicator({ progress }, modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }, color)
}

@Composable
fun CircularProgressIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, strokeWidth: Dp = 3.dp) {
    val transition = rememberInfiniteTransition(label = "loading")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "rotation")
    Canvas(modifier.size(32.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        drawArc(color, rotation, 240f, false, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
    }
}
