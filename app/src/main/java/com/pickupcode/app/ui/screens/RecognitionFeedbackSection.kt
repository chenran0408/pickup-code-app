package com.pickupcode.app.ui.screens

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
import com.pickupcode.app.feedback.FeedbackSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RecognitionFeedbackSection() {
    var feedback by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("识别反馈", style = MaterialTheme.typography.titleMedium)
            Text("识别漏了或识别错了，可以保存脱敏样例帮助改进。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { feedback = true }, modifier = Modifier.fillMaxWidth()) { Text("反馈漏识别或识别错误") }
            Text("更新记录：保留已取历史，新增过期筛选和通知监听状态，简化主页。", style = MaterialTheme.typography.bodySmall)
        }
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
