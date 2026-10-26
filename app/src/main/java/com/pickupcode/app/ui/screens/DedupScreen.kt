package com.pickupcode.app.ui.screens

import com.pickupcode.app.ui.components.IconText
import com.pickupcode.app.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import com.pickupcode.app.ui.miuix.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pickupcode.app.data.AppDatabase
import com.pickupcode.app.data.CodeHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DedupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val db = AppDatabase.getInstance(context)
    var groups by remember { mutableStateOf<Map<String, List<CodeHistory>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun reload() {
        val raw = withContext(Dispatchers.IO) { db.repository.getDuplicateEntries() }
        // 按码值和来源聚合：每组列出相同的取件记录。
        groups = raw.groupBy { listOf(it.code, it.type, it.pickupAddress, it.source, it.trackingNumber).joinToString("\u0000") }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "重复记录",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(MiuixIcons.Back, "返回") }
                }
            )
        },
        snackbarHost = { SnackbarHost(state = snackbar) }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没有重复记录", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("所有取餐码/取件码都是唯一的。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "summary") {
                    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("${groups.size} 组重复记录 · 共 ${groups.values.sumOf { it.size }} 条", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text("选择要保留的记录，其余记录会永久删除。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(groups.entries.toList(), key = { it.key }) { (key, entries) ->
                    DuplicateGroupCard(entries = entries, onDeleteDuplicates = { ids ->
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { db.repository.deleteByIds(ids) }
                                reload()
                                snackbar.showSnackbar("已整理 ${ids.size} 条重复记录")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                snackbar.showSnackbar("整理失败，请重试")
                            }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(entries: List<CodeHistory>, onDeleteDuplicates: (List<Long>) -> Unit) {
    if (entries.isEmpty()) return
    val code = entries.first().code
    val type = entries.first().type
    var keepEntry by remember(entries.map { it.id }) { mutableStateOf<CodeHistory?>(null) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            val icon = when (type) {
                "pickup_food" -> R.drawable.ic_cup_soda
                "coupon" -> R.drawable.ic_ticket
                else -> R.drawable.ic_package
            }
            IconText(icon, "$code  (${entries.size} 条重复)",
                iconSize = 18.dp,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            entries.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${e.source} · ${formatDedupTime(e.timestamp)}", style = MaterialTheme.typography.bodySmall)
                        if (e.pickupAddress.isNotBlank()) Text(e.pickupAddress, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (e.cabinetNumber.isNotBlank()) Text("柜号 ${e.cabinetNumber}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { keepEntry = e }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(MiuixIcons.Basic.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保留这条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }

    keepEntry?.let { selected ->
        AlertDialog(
            onDismissRequest = { keepEntry = null },
            title = { Text("删除其余重复记录？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        keepEntry = null
                        onDeleteDuplicates(entries.filter { it.id != selected.id }.map { it.id })
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(MiuixIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("永久删除其余 ${entries.size - 1} 条", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { keepEntry = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") }
            },
        )
    }
                }
            }
        }
    }

}

private val DEDUP_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private fun formatDedupTime(epochMillis: Long): String {
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DEDUP_TIME_FORMATTER)
}
