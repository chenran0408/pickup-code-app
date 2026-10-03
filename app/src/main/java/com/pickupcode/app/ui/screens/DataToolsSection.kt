package com.pickupcode.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pickupcode.app.BuildConfig
import com.pickupcode.app.backup.BackupCodec
import com.pickupcode.app.backup.BackupManager
import com.pickupcode.app.feedback.FeedbackSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DataToolsSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupCodec.Payload?>(null) }
    var restoreSettings by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { BackupManager.export(ctx, uri); result = "备份已保存" }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { result = "备份失败，请检查文件位置和可用空间" }
            finally { busy = false }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { preview = BackupManager.read(ctx, uri); restoreSettings = false; result = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { result = "无法恢复：备份格式无效、版本不支持或文件过大" }
            finally { busy = false }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("数据与反馈", style = MaterialTheme.typography.titleMedium)
            Text("备份记录、常用地址、规则及本地设置，不含截图、API 配置和系统授权。文件包含取件码和地址，请妥善保存。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !busy, onClick = { export.launch("码上闪记-备份-${java.time.LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("备份到文件") }
            OutlinedButton(enabled = !busy, onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("从文件恢复") }
            OutlinedButton(enabled = !busy, onClick = { feedback = true }, modifier = Modifier.fillMaxWidth()) { Text("反馈漏识别或识别错误") }
            Text("更新记录：保留已取历史，新增过期筛选、监听状态、识别反馈与本地备份。", style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    preview?.let { payload ->
        AlertDialog(onDismissRequest = { if (!busy) preview = null }, title = { Text("恢复备份？") },
            text = { Column {
                Text("文件包含 ${payload.records.size} 条记录、${payload.addresses.length()} 个常用地址。追加缺少的数据，重复导入不会重复添加，也不会覆盖已有记录和地址。")
                Row { Checkbox(checked = restoreSettings, enabled = !busy, onCheckedChange = { restoreSettings = it }); Text("同时恢复本地设置与规则") }
                Text("系统授权需要在本机重新开启。恢复后回收站记录仍按原删除时间清理。", style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try { val count = BackupManager.restore(ctx, payload, restoreSettings); result = "恢复完成，新增 $count 条记录"; preview = null }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { result = "恢复未完成；已有记录不会覆盖，可再次导入补齐"; preview = null }
                    finally { busy = false }
                }
            }) { Text("恢复") } }, dismissButton = { TextButton(enabled = !busy, onClick = { preview = null }) { Text("取消") } })
    }
    if (feedback) FeedbackDialog(onDismiss = { feedback = false })
}

@Composable
private fun FeedbackDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("漏识别") }; var source by remember { mutableStateOf("短信") }
    var original by remember { mutableStateOf("") }; var expected by remember { mutableStateOf("") }
    var sanitized by remember { mutableStateOf<String?>(null) }; var checked by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val report = FeedbackSanitizer.report(kind, source, sanitized.orEmpty(), expected, BuildConfig.VERSION_NAME)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(report.toByteArray(Charsets.UTF_8)) } ?: error("无法写入")
                }
                outcome = "反馈文件已保存，可在本对话中提供给开发者"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { outcome = "保存失败，请重试" }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("识别反馈") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sanitized == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("漏识别", "识别错误").forEach { value -> FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(value) }) } }
                OutlinedTextField(value = source, onValueChange = { source = it.take(50) }, label = { Text("入口：短信、微信、截图或分享") })
                OutlinedTextField(value = original, onValueChange = { original = it.take(20000) }, label = { Text("粘贴有问题的消息") }, maxLines = 5)
            } else {
                Text("数字已替换，请继续修改姓名和地址，保持取件码的位数与分隔符。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = sanitized.orEmpty(), onValueChange = { sanitized = it.take(20000); checked = false }, label = { Text("可编辑的脱敏样例") }, maxLines = 5)
                OutlinedTextField(value = expected, onValueChange = { expected = it.take(2000); checked = false }, label = { Text("期望识别结果（使用假码和假地址）") }, maxLines = 2)
                Row { Checkbox(checked = checked, onCheckedChange = { checked = it }); Text("已检查样例和期望结果，没有真实个人信息") }
            }
            outcome?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(enabled = if (sanitized == null) original.isNotBlank() else checked, onClick = {
        if (sanitized == null) { sanitized = FeedbackSanitizer.maskNumbers(original); original = ""; source = FeedbackSanitizer.maskNumbers(source) }
        else save.launch("码上闪记-识别反馈.txt")
    }) { Text(if (sanitized == null) "生成脱敏样例" else "保存反馈文件") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
