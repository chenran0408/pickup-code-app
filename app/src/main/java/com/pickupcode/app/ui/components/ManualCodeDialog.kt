package com.pickupcode.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.pickupcode.app.extractor.CodeValidator
import com.pickupcode.app.ui.screens.home.PendingShareText

/** 单码与分享文本共用一个输入框；剪贴板有分享文本时直接给出导入入口。 */
@Composable
fun ManualCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (code: String, type: String, source: String) -> Unit,
    onImport: (List<PendingShareText.Entry>, (Boolean) -> Unit) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var previewingClipboard by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val clipboardText = remember { clipboard.getText()?.text?.toString().orEmpty() }
    val clipboardParsed = remember(clipboardText) { PendingShareText.parse(clipboardText) }
    val isImportText = input.trimStart().startsWith("当前未取码（")
    val parsed = remember(input) { if (isImportText) PendingShareText.parse(input) else PendingShareText.ParseResult() }
    val typeOverrides = remember(input) { mutableStateMapOf<Int, String>() }
    val entries = parsed.entries.mapIndexed { index, entry -> entry.copy(type = typeOverrides[index] ?: entry.type) }
    val invalidEntry = entries.indexOfFirst { !CodeValidator.isValidManualCode(it.code, it.type) }
    val autoFood = input.trim().let { it.length in 2..3 && it.all(Char::isDigit) &&
        CodeValidator.isValidManualCode(it, "pickup_food") }
    val codeType = selectedType ?: if (autoFood) "pickup_food" else "pickup_parcel"
    val validSingle = !isImportText && CodeValidator.isValidManualCode(input.trim(), codeType)
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (clipboardParsed.entries.isEmpty()) focus.requestFocus() }
    LaunchedEffect(isImportText) { if (isImportText) { focusManager.clearFocus(force = true); keyboard?.hide() } }
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    fun startImport(items: List<PendingShareText.Entry>) {
        importing = true
        onImport(items) { success ->
            importing = false
            if (success) onDismiss()
        }
    }

    fun leavePreviewOrDismiss() {
        if (previewingClipboard) {
            previewingClipboard = false
            input = ""
        } else onDismiss()
    }

    AlertDialog(
        onDismissRequest = { leavePreviewOrDismiss() },
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (previewingClipboard) "确认导入" else "添加码")
                if (!isImportText && clipboardParsed.entries.isNotEmpty()) {
                    TextButton(onClick = {
                        input = clipboardText.take(30_001)
                        previewingClipboard = true
                    }, enabled = !importing) {
                        Text("导入剪贴板")
                    }
                }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (previewingClipboard) {
                    Text("剪贴板中识别到 ${entries.size} 条，请核对后导入",
                        style = MaterialTheme.typography.bodyMedium)
                } else {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(30_001) },
                        label = { Text(if (isImportText) "分享文本" else "取件码 / 取餐码") },
                        placeholder = { Text("输入码值，或粘贴分享导出的整段文本") },
                        singleLine = !isImportText,
                        maxLines = if (isImportText) 7 else 1,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }

                if (isImportText) {
                    if (parsed.error != null) Text(parsed.error,
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else {
                        if (!previewingClipboard) Text("识别到 ${entries.size} 条，导入前可点类型修改",
                            style = MaterialTheme.typography.titleSmall)
                        if (parsed.legacyCount > 0) Text("旧版文本有 ${parsed.legacyCount} 条缺少类型，请核对取件/取餐。",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (invalidEntry >= 0) Text("第 ${invalidEntry + 1} 条码值不适用于所选类型",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        entries.forEachIndexed { index, entry ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("${entry.source} · ${entry.code}" +
                                        entry.cabinet.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(entry.address.ifBlank { "未填地址" } +
                                        if (entry.expired) " · 已过期" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = {
                                    typeOverrides[index] = if (entry.type == "pickup_food") "pickup_parcel" else "pickup_food"
                                }) { Text(if (entry.type == "pickup_food") "取餐" else "取件") }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(value = source, onValueChange = { source = it },
                        label = { Text("来源（可选）") }, placeholder = { Text("如：菜鸟驿站") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = codeType == "pickup_parcel",
                            onClick = { selectedType = "pickup_parcel" }, label = { Text("取件") }, colors = chipColors)
                        FilterChip(selected = codeType == "pickup_food",
                            onClick = { selectedType = "pickup_food" }, label = { Text("取餐") }, colors = chipColors)
                    }
                }
            }
        },
        confirmButton = {
            if (isImportText) {
                TextButton(onClick = { startImport(entries) },
                    enabled = parsed.error == null && entries.isNotEmpty() && invalidEntry < 0 && !importing) {
                    Text(if (importing) "导入中…" else "确认导入")
                }
            } else {
                TextButton(onClick = {
                    if (validSingle) {
                        val label = source.ifBlank { if (codeType == "pickup_food") "手动录入·取餐" else "手动录入·取件" }
                        onConfirm(input.trim(), codeType, label)
                        onDismiss()
                    }
                }, enabled = validSingle) { Text(if (input.isNotBlank() && !validSingle) "格式不符" else "添加") }
            }
        },
        dismissButton = { TextButton(onClick = { leavePreviewOrDismiss() }) {
            Text(if (previewingClipboard) "返回" else "取消")
        } }
    )
}
