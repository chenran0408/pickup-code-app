package com.pickupcode.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import com.pickupcode.app.ui.miuix.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pickupcode.app.learner.CommonStationStore
import com.pickupcode.app.learner.SavedAddressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「常用取件地址」（预存地址）管理页。
 *
 * 模型就两个字段（用户 2026-09-15 的想法）：
 *  - **完整名称**：命中后写进记录、在主页卡片显示的那个值
 *  - **关键词**（一个至多个）：唯一匹配依据；OCR 文本命中任一关键词 → 用完整名称
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedAddressScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var items by remember { mutableStateOf<List<SavedAddressStore.SavedAddress>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<SavedAddressStore.SavedAddress?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var importCandidates by remember { mutableStateOf<List<String>>(emptyList()) }

    suspend fun reload() {
        items = withContext(Dispatchers.IO) { SavedAddressStore.getAll(context) }
        loading = false
    }

    fun startImport() {
        scope.launch {
            val candidates = withContext(Dispatchers.IO) {
                val stations = CommonStationStore.getCommonStations(context).map { it.name }
                val points = CommonStationStore.getPickupPoints(context).map { it.name }
                (points + stations).filter { it.isNotBlank() }.distinct().take(30)
            }
            if (candidates.isEmpty()) snackbar.showSnackbar("还没有可导入的历史记录")
            else { importCandidates = candidates; showImport = true }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "常用取件地址",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editing = null
                        showEditor = true
                    }) { Icon(MiuixIcons.Add, contentDescription = "新增") }
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(MiuixIcons.More, contentDescription = "更多地址操作") }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("从历史导入") },
                                onClick = { moreMenu = false; startImport() },
                                leadingIcon = { Icon(MiuixIcons.Import, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("全部清除", color = MaterialTheme.colorScheme.error) },
                                onClick = { moreMenu = false; showClearConfirmation = true },
                                leadingIcon = { Icon(MiuixIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { top.yukonga.miuix.kmp.basic.SnackbarHost(state = snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "添加常去的驿站或快递柜。识别到你填写的关键词时，取件记录会显示对应地址；地址只保存在这台手机上。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有预存地址\n点右上角 + 添加，或从历史导入", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        var menu by remember(item.id) { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier.weight(1f).clickable {
                                        editing = item
                                        showEditor = true
                                    }.padding(vertical = 8.dp)
                                ) {
                                    Text(item.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        buildString {
                                            append(if (item.keywords.isEmpty()) "按地址名称识别" else "识别词：${item.keywords.joinToString("、")}")
                                            if (item.origin == "promoted") append(" · 从历史添加")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = item.enabled,
                                    onCheckedChange = { on ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) { SavedAddressStore.setEnabled(context, item.id, on) }
                                            reload()
                                        }
                                    }
                                )
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(MiuixIcons.More, "${item.fullName}更多操作") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("编辑地址") }, onClick = {
                                            menu = false; editing = item; showEditor = true
                                        })
                                        DropdownMenuItem(text = { Text("删除地址", color = MaterialTheme.colorScheme.error) }, onClick = {
                                            menu = false
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) { SavedAddressStore.delete(context, item.id) }
                                                    reload()
                                                    val result = snackbar.showSnackbar(
                                                        message = "已删除地址",
                                                        actionLabel = "撤销",
                                                        duration = SnackbarDuration.Long,
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        withContext(Dispatchers.IO) { SavedAddressStore.upsert(context, item) }
                                                        reload()
                                                        snackbar.showSnackbar("已恢复地址")
                                                    }
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (_: Exception) {
                                                    snackbar.showSnackbar("操作失败，请重试")
                                                }
                                            }
                                        }, leadingIcon = { Icon(MiuixIcons.Delete, null, tint = MaterialTheme.colorScheme.error) })
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        SavedAddressEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onConfirm = { fullName, keywords ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        SavedAddressStore.upsert(
                            context,
                            (editing ?: SavedAddressStore.SavedAddress(fullName = fullName))
                                .copy(
                                    fullName = fullName,
                                    keywords = keywords.split(",", "，", "、").map { it.trim() }.filter { it.isNotBlank() }
                                )
                        )
                    }
                    showEditor = false
                    reload()
                }
            }
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("清除全部常用地址？") },
            text = { Text("所有手动保存和自动提升的常用地址都会移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        scope.launch {
                            withContext(Dispatchers.IO) { SavedAddressStore.clear(context) }
                            reload()
                            snackbar.showSnackbar("已清除全部常用地址")
                        }
                    },
                ) { Text("全部清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("取消") } },
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("从历史记录导入") },
            text = {
                Column {
                    Text(
                        "导入后可编辑地址名称和识别词，让应用更容易找到对应地点。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(importCandidates) { name ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                SavedAddressStore.upsert(
                                                    context,
                                                    SavedAddressStore.SavedAddress(
                                                        fullName = name,
                                                        keywords = listOf(name),
                                                        origin = "promoted"
                                                    )
                                                )
                                            }
                                            showImport = false
                                            reload()
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            ) { Text(name, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            SavedAddressStore.upsertAll(
                                context,
                                importCandidates.map {
                                    SavedAddressStore.SavedAddress(
                                        fullName = it,
                                        keywords = listOf(it),
                                        origin = "promoted"
                                    )
                                }
                            )
                        }
                        showImport = false
                        reload()
                    }
                }) { Text("全部导入") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SavedAddressEditorDialog(
    initial: SavedAddressStore.SavedAddress?,
    onDismiss: () -> Unit,
    onConfirm: (fullName: String, keywords: String) -> Unit
) {
    var fullName by remember { mutableStateOf(initial?.fullName ?: "") }
    var keywords by remember { mutableStateOf(initial?.keywords?.joinToString(",") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增常用地址" else "编辑常用地址") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    labelText = "地址名称",
                    placeholder = { Text("如：长兴路北段菜鸟驿站") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    labelText = "识别词，用逗号分隔",
                    placeholder = { Text("如：北段驿站,长兴路") },
                    singleLine = true
                )
                Text(
                    "识别词留空时，应用会根据地址名称查找。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(fullName.trim(), keywords) },
                enabled = fullName.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
