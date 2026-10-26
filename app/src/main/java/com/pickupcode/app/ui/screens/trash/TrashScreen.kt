package com.pickupcode.app.ui.screens.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonColors as MiuixButtonColors
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.pickupcode.app.ui.miuix.Card
import com.pickupcode.app.ui.miuix.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.pickupcode.app.ui.miuix.Icon
import com.pickupcode.app.ui.miuix.IconButton
import com.pickupcode.app.ui.miuix.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.pickupcode.app.ui.miuix.Scaffold
import com.pickupcode.app.ui.miuix.Text
import com.pickupcode.app.ui.miuix.TextButton
import com.pickupcode.app.ui.miuix.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pickupcode.app.data.AppDatabase
import com.pickupcode.app.data.CodeHistory
import com.pickupcode.app.service.CodeCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val trashHistory by db.repository.observeTrash().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "回收站",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(state = snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                "回收站记录保留24小时，已取历史在主页查看",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (trashHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "回收站是空的",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f) // 占满 Column 剩余空间，避免列表/空状态布局错位
                        .padding(horizontal = 16.dp)
                ) {
                    items(trashHistory, key = { it.id }) { item ->
                        TrashCard(
                            item = item,
                            onRestore = {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) { CodeCompletion.restore(context, db.repository, item.id) }
                                    }.onSuccess {
                                        snackbar.showSnackbar("已恢复")
                                    }.onFailure {
                                        snackbar.showSnackbar("恢复失败，请重试")
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) { db.repository.deleteById(item.id) }
                                    }.onSuccess {
                                        snackbar.showSnackbar("已永久删除")
                                    }.onFailure {
                                        snackbar.showSnackbar("删除失败，请重试")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashCard(
    item: CodeHistory,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirmation by remember(item.id) { mutableStateOf(false) }
    // H8: 组合期只算一次会导致倒计时/进度条冻结；用 tick 每分钟刷新
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            tick = System.currentTimeMillis()
        }
    }
    val remaining = item.doneAt + 24 * 60 * 60 * 1000 - tick
    val progress = (remaining.toFloat() / (24 * 60 * 60 * 1000)).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = item.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough
            )
            Text(
                text = "${item.source} · ${formatTime(item.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp), // 与默认 strokeWidth(4dp) 一致，避免 3dp 裁剪 stroke 导致横线错位
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "剩余 ${formatRemaining(item.doneAt, tick)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                MiuixButton(
                    onClick = onRestore,
                    minHeight = 48.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    colors = MiuixButtonColors(
                        color = MiuixTheme.colorScheme.primary,
                        disabledColor = MiuixTheme.colorScheme.secondaryVariant,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                        disabledContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    ),
                ) {
                    Icon(MiuixIcons.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("恢复")
                }
                TextButton(onClick = { showDeleteConfirmation = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(MiuixIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("永久删除这条记录？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") } },
        )
    }
}

// 同 CodeHistoryCard：格式化器放顶层，别在函数体里 ofPattern（回收站同样是长列表）
private val TRASH_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun formatTime(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(TRASH_TIME_FORMATTER)
}

private fun formatRemaining(doneAt: Long, now: Long): String {
    val remaining = doneAt + 24 * 60 * 60 * 1000 - now
    if (remaining <= 0) return "即将删除"
    val hours = remaining / (60 * 60 * 1000)
    val minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000)
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}
