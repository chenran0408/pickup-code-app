package com.pickupcode.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.pickupcode.app.share.ShareRecognitionSession

@Composable
fun SharedImageResultDialog(feedback: ShareRecognitionSession.Feedback,
    onDismiss: () -> Unit, onDetail: (Long) -> Unit, onManual: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (feedback.processing) "正在识别图片" else "图片识别结果") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (feedback.processing) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Text("正在读取图片并查找码值，请稍候")
                } else {
                    if (feedback.records.isNotEmpty()) {
                        Text("已保存 ${feedback.records.size - feedback.existingCount} 条" +
                            if (feedback.existingCount > 0) "，已有 ${feedback.existingCount} 条" else "")
                        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(feedback.records, key = { it.id }) { item ->
                                OutlinedCard {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        RecordStatusBadge(item)
                                        Text(item.code, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(item.source.ifBlank { if (item.type == "pickup_food") "取餐码" else if (item.type == "coupon") "券码" else "取件码" })
                                        if (item.pickupAddress.isNotBlank()) Text(item.pickupAddress)
                                        Row {
                                            TextButton(onClick = {
                                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("码值", item.code))
                                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                            }) { Text("复制") }
                                            TextButton(onClick = { onDetail(item.id) }) { Text("查看详情") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (feedback.message.isNotBlank()) Text(feedback.message)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (feedback.processing) "后台识别" else "完成") } },
        dismissButton = {
            if (!feedback.processing && feedback.records.isEmpty()) TextButton(onClick = onManual) { Text("手动添加") }
        })
}
