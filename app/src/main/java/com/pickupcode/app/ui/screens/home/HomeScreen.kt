package com.pickupcode.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Settings
import com.pickupcode.app.ui.miuix.Button
import com.pickupcode.app.ui.miuix.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.pickupcode.app.ui.miuix.Icon
import com.pickupcode.app.ui.miuix.IconButton
import androidx.compose.material3.MaterialTheme
import com.pickupcode.app.ui.miuix.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import com.pickupcode.app.ui.miuix.Text
import com.pickupcode.app.ui.miuix.TextButton
import com.pickupcode.app.ui.miuix.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.pickupcode.app.ui.miuix.*
import com.pickupcode.app.ui.theme.UiSpacing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.compose.ui.unit.sp
import com.pickupcode.app.R
import com.pickupcode.app.ui.components.IconText
import com.pickupcode.app.ui.components.PickupIdentityCard
import top.yukonga.miuix.kmp.basic.FloatingActionButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.icon.extended.Add
import com.pickupcode.app.data.AppDatabase
import com.pickupcode.app.data.CodeHistory
import com.pickupcode.app.ui.theme.TypeCoupon
import com.pickupcode.app.ui.theme.TypeFood
import com.pickupcode.app.ui.theme.TypeParcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hasNotificationPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    accessibilityEnabledInSettings: Boolean,
    hideAccessibilityCard: Boolean,
    hideGuideCard: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onHideAccessibilityCard: () -> Unit,
    onHideGuideCard: () -> Unit,
    onSettingsClick: () -> Unit,
    onIdentityClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onFabClick: () -> Unit,
    bottomNavigationBar: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    // 注册到 ViewModelStore，使 viewModelScope 随 Activity/导航正确 onCleared（勿用 remember 假 VM）
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(db.repository))
    val activeHistory by vm.activeHistory.collectAsStateWithLifecycle()
    val completedHistory by vm.completedHistory.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var typeFilter by rememberSaveable { mutableStateOf("all") }
    var guideExpanded by remember { mutableStateOf(false) }
    // 分组方式：time=按时间 / address=按地址聚合（rememberSaveable：旋转屏幕保持）
    var groupMode by rememberSaveable { mutableStateOf("address") }
    var completedOnly by rememberSaveable { mutableStateOf(false) }
    var expiredOnly by rememberSaveable { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000) }
        }
    }
    val clipboard = LocalClipboardManager.current
    fun copyCode(item: CodeHistory) {
        clipboard.setText(AnnotatedString(item.code))
        scope.launch { snackbarHostState.showSnackbar("码值已复制") }
    }
    fun sharePending() {
        val shareText = PendingShareText.build(activeHistory, System.currentTimeMillis())
        if (shareText == null) {
            scope.launch { snackbarHostState.showSnackbar("当前没有未取的取件码或取餐码") }
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        try {
            context.startActivity(Intent.createChooser(send, "分享未取码"))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar("没有可用的分享应用") }
        }
    }
    var filterMenu by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val todayStart = remember(today) { today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val yesterdayStart = remember(today) { today.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val projection by androidx.compose.runtime.produceState(HomeListProjection(), activeHistory, completedHistory,
        typeFilter, completedOnly, expiredOnly, groupMode, now, todayStart, yesterdayStart) {
        val next = withContext(Dispatchers.Default) {
            HomeListProjection.build(activeHistory, completedHistory, typeFilter, completedOnly, expiredOnly,
                groupMode, now, todayStart, yesterdayStart)
        }
        value = next
    }
    val filteredHistory = projection.records
    val grouped = projection.timeGroups
    val groupOrder = listOf("今天", "昨天", "更早").filter { it in grouped }
    val addressGroups = projection.addressGroups

    LaunchedEffect(Unit) { vm.cleanExpired() }

    // 已取保留历史；删除进回收站；操作后可撤销。
    fun markAsDone(item: CodeHistory) {
        vm.markAsDone(item,
            onSuccess = { batch ->
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "已标记已取，可在已取列表恢复",
                        actionLabel = "撤销",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.undoDone(batch) { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
                    }
                }
            },
            onError = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short) }
            }
        )
    }

    fun markGroupDone(items: List<CodeHistory>) {
        vm.markGroupDone(items, onSuccess = { batch ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    "已标记 ${batch.items.size} 件",
                    "撤销",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    vm.undoDone(batch) { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
                }
            }
        }, onError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } })
    }


    top.yukonga.miuix.kmp.basic.Scaffold(
        topBar = {
            TopAppBar(
                title = "码上闪记",
                actions = {
                    // 右上角三个辅助动作图标：2026-09-18 用户指定换成附件里的线性图标（Lucide 风格），
                    // 统一用 onSurface 上色（比默认的 onSurfaceVariant 更深）+ 20dp（用户反馈 24dp 偏大）
                    // + 同一个 IconButton 热区。
                    val actionTint = MaterialTheme.colorScheme.onSurface
                    val iconModifier = Modifier.size(20.dp)
                    IconButton(onClick = onSettingsClick) {
                        Icon(MiuixIcons.Settings, "设置", modifier = iconModifier, tint = actionTint)
                    }
                    IconButton(onClick = ::sharePending) {
                        Icon(MiuixIcons.Share, contentDescription = "分享当前未取码", modifier = iconModifier,
                            tint = actionTint)
                    }

                },
            )
        },
        bottomBar = bottomNavigationBar,
        floatingActionButton = {
            top.yukonga.miuix.kmp.basic.FloatingActionButton(
                onClick = onFabClick,
                modifier = Modifier.semantics {
                    contentDescription = "手动添加取件码或取餐码"
                },
                minWidth = FloatingActionButtonDefaults.MinWidth,
                minHeight = FloatingActionButtonDefaults.MinHeight,
            ) {
                        Icon(top.yukonga.miuix.kmp.icon.MiuixIcons.Add, null)
            }
        }
    ) { padding ->
        // 顶部提示层：snackbar（"已移至回收站"/错误提示）固定显示在界面上方（顶部栏之下），
        // 不再默认贴屏幕底部（用户要求：弹窗放到界面上方）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = FloatingActionButtonDefaults.MinHeight + UiSpacing.page)
            ) {
            item(key = "identity_shortcuts", contentType = "identity_shortcuts") {
                Column(Modifier.fillMaxWidth().padding(top = UiSpacing.compact)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = UiSpacing.page),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("取件身份码", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onIdentityClick, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("全部身份码  ›", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    PickupIdentityCard(compact = true)
                }
            }
            item(key = "filters", contentType = "controls") {
                Row(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.page, vertical = UiSpacing.compact), horizontalArrangement = Arrangement.spacedBy(UiSpacing.item)) {
                    Box {
                        val filterLabel = when {
                            completedOnly -> "已取 ${completedHistory.size}"
                            expiredOnly -> "过期记录"
                            typeFilter == "food" -> "取餐码"
                            typeFilter == "parcel" -> "取件码"
                            typeFilter == "coupon" -> "券码"
                            else -> "全部"
                        }
                        TextButton(onClick = { filterMenu = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(MiuixIcons.Filter, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("$filterLabel ▾", color = if (typeFilter != "all" || completedOnly || expiredOnly)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                            listOf("all" to "全部", "parcel" to "取件码", "food" to "取餐码", "coupon" to "券码")
                                .forEach { (key, label) ->
                                    DropdownMenuItem(text = { Text(if (typeFilter == key && !completedOnly && !expiredOnly) "✓  $label" else label) },
                                        onClick = { typeFilter = key; completedOnly = false; expiredOnly = false; filterMenu = false })
                                }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(if (completedOnly) "✓  已取 ${completedHistory.size}" else "已取 ${completedHistory.size}") },
                                onClick = { completedOnly = true; expiredOnly = false; filterMenu = false })
                            DropdownMenuItem(text = { Text(if (expiredOnly) "✓  过期记录" else "过期记录") },
                                onClick = { completedOnly = false; expiredOnly = true; filterMenu = false })
                            if (typeFilter != "all" || completedOnly || expiredOnly) {
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("清除筛选") }, onClick = {
                                    typeFilter = "all"; completedOnly = false; expiredOnly = false; filterMenu = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { groupMode = if (groupMode == "address") "time" else "address" },
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = if (groupMode == "address") "当前按地址分组，切换为按时间" else "当前按时间分组，切换为按地址"
                        },
                    ) {
                        Icon(MiuixIcons.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (groupMode == "address") "按地址" else "按时间")
                    }
                }
            }

            // Setup items share one status surface so permission notices do not stack as separate cards.
            if (!hasNotificationPermission || !hideAccessibilityCard) {
                item(key = "setup_status") {
                    val needsAccessibilityRestart = accessibilityEnabledInSettings && !isAccessibilityEnabled
                    val containerColor = when {
                        !hasNotificationPermission || needsAccessibilityRestart -> MaterialTheme.colorScheme.errorContainer
                        isAccessibilityEnabled -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = UiSpacing.page, vertical = UiSpacing.compact),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!hasNotificationPermission) {
                                IconText(R.drawable.ic_bell, "需要通知权限", style = MaterialTheme.typography.titleSmall)
                                Text("开启后才能在锁屏和通知栏看到取件码。", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = onRequestNotificationPermission, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("授权通知")
                                }
                            }
                            if (!hideAccessibilityCard) {
                                if (!hasNotificationPermission) HorizontalDivider()
                                val title = when {
                                    isAccessibilityEnabled -> "自动识别已开启"
                                    needsAccessibilityRestart -> "自动识别服务未在运行"
                                    else -> "开启屏幕自动识别"
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = onHideAccessibilityCard, modifier = Modifier.size(48.dp)) {
                                        Icon(MiuixIcons.Basic.Close, contentDescription = "隐藏自动识别提示",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (isAccessibilityEnabled) {
                                    Text("点快捷设置里的码上闪记磁贴，即可识别当前屏幕。",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text(if (needsAccessibilityRestart)
                                        "系统设置显示已开启，但服务未运行。重新关闭并开启码上闪记服务。"
                                    else "开启后可从快捷设置磁贴识别屏幕上的取餐码和取件码。",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(onClick = onEnableAccessibility, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(if (needsAccessibilityRestart) "重新开启" else "去开启")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 空状态
            if (filteredHistory.isEmpty()) {
                item {
                    val filtersActive = typeFilter != "all" || completedOnly || expiredOnly
                    if (filtersActive) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("当前条件下没有记录",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = {
                                    typeFilter = "all"
                                    completedOnly = false
                                    expiredOnly = false
                                }) {
                                    Text("清除筛选")
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("还没有取件码",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 列表：按地址聚合 / 按时间分组
            if (groupMode == "address") {
                addressGroups.forEach { (addr, groupItems) ->
                    item(key = "addr_header_$addr", contentType = "group_header") {
                        AddressGroupHeader(address = addr.ifBlank { "未填地址" }, count = groupItems.size,
                        onDoneAll = if (!completedOnly && addr.isNotBlank() && groupItems.any { it.isActive }) ({ markGroupDone(groupItems.filter { it.isActive }) }) else null)
                    }
                    items(groupItems, key = { it.id }, contentType = { "record" }) { cardItem ->
                        CodeHistoryCard(
                            item = cardItem, now = now, onCopy = { copyCode(cardItem) },
                            onClick = { onItemClick(cardItem.id) },
                            onDone = { if (cardItem.isActive) markAsDone(cardItem) else vm.restoreCompleted(cardItem) { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } } },
                            onDelete = {
                                vm.delete(
                                    cardItem,
                                    onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                                    onSuccess = {
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar("已移至回收站", "撤销", duration = SnackbarDuration.Long)
                                            if (result == SnackbarResult.ActionPerformed) {
                                                vm.restoreCompleted(cardItem) { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                            }
                                        }
                                    },
                                )
                            }
                        )
                    }
                }
            } else {
                groupOrder.forEach { groupLabel ->
                    item(key = "header_$groupLabel", contentType = "group_header") {
                        TimeGroupHeader(label = groupLabel)
                    }
                    val groupItems = grouped[groupLabel] ?: emptyList()
                    items(groupItems, key = { it.id }, contentType = { "record" }) { item ->
                        CodeHistoryCard(
                            item = item, now = now, onCopy = { copyCode(item) },
                            onClick = { onItemClick(item.id) },
                            onDone = { if (item.isActive) markAsDone(item) else vm.restoreCompleted(item) { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } } },
                            onDelete = {
                                vm.delete(
                                    item,
                                    onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                                    onSuccess = {
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar("已移至回收站", "撤销", duration = SnackbarDuration.Long)
                                            if (result == SnackbarResult.ActionPerformed) {
                                                vm.restoreCompleted(item) { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                            }
                                        }
                                    },
                                )
                            }
                        )
                    }
                }
            }
            // 引导卡片（可折叠）
            if (!hideGuideCard && filteredHistory.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { guideExpanded = !guideExpanded }
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                MaterialTheme.shapes.medium
                            )
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconText(R.drawable.ic_download, "怎么添加取餐码/取件码/券码?",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f))
                            Text(if (guideExpanded) "▴" else "▾",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AnimatedVisibility(visible = guideExpanded) {
                            Column {
                                Spacer(Modifier.height(6.dp))
                                Text("·从短信/聊天 App 分享", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("（在短信/聊天里长按选中文字或点分享 → 选「码上闪记」）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("·点「添加码」手动录入", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("·开启屏幕识别后，可从快捷设置识别当前页面", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = onHideGuideCard,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("我知道了")
                                }
                            }
                        }
                    }
                }
            }

            }  // LazyColumn 结束
            // 顶部提示层：画在列表之上，固定在界面上方（顶部栏之下）
            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }  // Box 结束
    }
}

