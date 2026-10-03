package com.pickupcode.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import com.pickupcode.app.extractor.CodeValidator

/**
 * 手动输入取餐码/取件码的对话框
 */
@Composable
fun ManualCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (code: String, type: String, source: String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var codeType by remember { mutableStateOf("pickup_food") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动录入") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 类型选择
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = codeType == "pickup_food",
                        onClick = { codeType = "pickup_food" },
                        label = { Text("取餐码") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = codeType == "pickup_parcel",
                        onClick = { codeType = "pickup_parcel" },
                        label = { Text("取件码") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("来源（品牌/驿站）") },
                    placeholder = { Text("如：瑞幸、菜鸟驿站") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("取餐码/取件码") },
                    placeholder = { Text("如：A-356 或 10-2-7507") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
        },
        confirmButton = {
            // 格式白名单：与 AI 路径对齐——取餐码允许 2-3 位纯数字（瑞幸/蜜雪），仍过内容噪声检查
            val valid = code.trim().let { CodeValidator.isValidManualCode(it, codeType) }
            TextButton(
                onClick = {
                    if (valid) {
                        val src = source.ifBlank {
                            if (codeType == "pickup_food") "手动录入·取餐" else "手动录入·取件"
                        }
                        onConfirm(code.trim(), codeType, src)
                        onDismiss()
                    }
                },
                enabled = valid
            ) {
                Text(if (code.isNotBlank() && !valid) "格式不符" else "确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
