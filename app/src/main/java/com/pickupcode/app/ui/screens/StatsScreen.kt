package com.pickupcode.app.ui.screens

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import com.pickupcode.app.ui.components.IconText
import com.pickupcode.app.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AssistChipDefaults
import com.pickupcode.app.ui.miuix.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pickupcode.app.learner.DailyStats
import com.pickupcode.app.learner.PatternLearner
import com.pickupcode.app.learner.PatternLearner.PatternSuggestion
import com.pickupcode.app.learner.PatternLearner.PatternStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var stats by remember { mutableStateOf<PatternStats?>(null) }
    var suggestions by remember { mutableStateOf<List<PatternSuggestion>>(emptyList()) }
    var learnedRules by remember { mutableStateOf<List<PatternLearner.LearnedRule>>(emptyList()) }
    var dailyStats by remember { mutableStateOf<List<DailyStats.DayStat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun reloadLearned() {
        scope.launch {
            learnedRules = withContext(Dispatchers.IO) { PatternLearner.getLearnedPatterns(context) }
        }
    }

    fun reloadDaily() {
        scope.launch {
            dailyStats = withContext(Dispatchers.IO) { DailyStats.getDailyStats(context) }
        }
    }

    LaunchedEffect(Unit) {
        try {
            stats = withContext(Dispatchers.IO) { PatternLearner.getStats(context) }
            suggestions = withContext(Dispatchers.IO) { PatternLearner.getSuggestions(context) }
            learnedRules = withContext(Dispatchers.IO) { PatternLearner.getLearnedPatterns(context) }
            dailyStats = withContext(Dispatchers.IO) { DailyStats.getDailyStats(context) }
            // Trigger auto-apply on view
            withContext(Dispatchers.IO) { PatternLearner.autoApply(context) }
        } catch (e: Exception) {
            stats = null
            Log.e("StatsScreen", "加载统计失败", e)
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "识别统计",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, "返回")
                    }
                },
                actions = {
                    // C4: 成绩卡分享（生成图片海报）
                    if (stats != null) {
                        IconButton(onClick = {
                            val s = stats ?: return@IconButton
                            scope.launch(Dispatchers.IO) {
                                com.pickupcode.app.share.ShareStatsCard.share(context, s)
                            }
                        }) {
                            Icon(MiuixIcons.Share, "分享成绩卡")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(state = snackbar) }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                stats?.let { s ->
                    OverviewCard(s)
                    PatternBreakdownCard(s)
                } ?: EmptyStateMessage()

                if (dailyStats.isNotEmpty()) {
                    HitRateCard(dailyStats)
                }

                SuggestionsCard(suggestions) { cleared ->
                    if (cleared) {
                        scope.launch {
                            suggestions = withContext(Dispatchers.IO) {
                                PatternLearner.getSuggestions(context)
                            }
                        }
                    }
                }

                LearnedRulesCard(
                    learnedRules,
                    onChanged = { reloadLearned() },
                    onDelete = { rule ->
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { PatternLearner.deleteRule(context, rule.regex) }
                                reloadLearned()
                                snackbar.showSnackbar("已删除规则")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                snackbar.showSnackbar("删除失败，请重试")
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(stats: PatternStats) {
    val hitRatio = if (stats.totalScans > 0) (stats.attempts.toFloat() / stats.totalScans).coerceIn(0f, 1f) else 0f
    val hitRate = (hitRatio * 100f).toInt()
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("识别成功率", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$hitRate%", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text("每次识别中找到取件码的比例", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (stats.totalScans > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { hitRatio },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("总扫描", stats.totalScans.toString())
                StatItem("已识别", stats.attempts.toString(), MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("未识别", stats.misses.toString(), MaterialTheme.colorScheme.error)
                StatItem("已确认", stats.verified.toString(), MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.widthIn(min = 96.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PatternBreakdownCard(stats: PatternStats) {
    val patterns = stats.perPattern.entries.sortedByDescending { it.value }
    if (patterns.isEmpty()) return

    val maxCount = patterns.maxOf { it.value }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("模式分布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            patterns.forEach { (id, count) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            patternLabel(id),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "$count 次",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { count.toFloat() / maxCount },
                        modifier = Modifier.width(80.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionsCard(suggestions: List<PatternSuggestion>, onCleared: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconText(R.drawable.ic_lightbulb, "识别改进建议", iconSize = 18.dp, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (suggestions.isNotEmpty()) {
                    TextButton(onClick = { showClearDialog = true }) {
                        Text("清除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (suggestions.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "暂时没有需要改进的内容。应用会在识别不到取件码时逐步适应新的格式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(8.dp))
                    suggestions.forEachIndexed { index, sug ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(sug.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                "${sug.count} 次出现",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "例如：${sug.sampleCodes.take(3).joinToString("、")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                    }
                    if (index < suggestions.lastIndex) HorizontalDivider()
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除未识别内容？") },
            text = { Text("这会删除应用尚未识别出的内容，并清除由此积累的学习记录。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { PatternLearner.clearUnmatched(context) }
                    showClearDialog = false
                    onCleared(true)
                }) { Text("确认清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyStateMessage() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("暂无统计数据", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "使用 App 识别取餐码/取件码后，统计数据会在这里展示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** B2: 命中率曲线卡片（Compose Canvas 手写折线图，不引第三方图表库）。 */
@Composable
private fun HitRateCard(stats: List<DailyStats.DayStat>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            IconText(R.drawable.ic_trending_up, "识别成功率趋势", iconSize = 18.dp, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("近 ${stats.size} 天找到取件码的比例", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (stats.size < 2) {
                Text("记录还不够多，继续使用后会显示趋势。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val lineColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                    val w = size.width
                    val h = size.height
                    val padB = 18.dp.toPx()
                    val padT = 8.dp.toPx()
                    val plotH = h - padB - padT
                    val maxRate = stats.mapNotNull { if (it.total > 0) it.hits.toFloat() / it.total else null }
                        .maxOrNull()?.coerceAtLeast(0.01f) ?: 0.01f
                    val n = stats.size
                    // 画水平网格线 + 基线
                    drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, h - padB), androidx.compose.ui.geometry.Offset(w, h - padB), strokeWidth = 1.dp.toPx())
                    // 逐点画命中率折线（y = 每日命中率，相对最高命中率归一化）
                    val points = stats.mapIndexed { i, s ->
                        val x = if (n == 1) w / 2f else w * i / (n - 1).toFloat()
                        val rate = if (s.total > 0) s.hits.toFloat() / s.total else 0f
                        val normalized = (rate / maxRate)
                        androidx.compose.ui.geometry.Offset(x, h - padB - plotH * normalized)
                    }
                    for (i in 1 until points.size) {
                        drawLine(lineColor, points[i - 1], points[i], strokeWidth = 2.5.dp.toPx())
                    }
                    for (p in points) {
                        drawCircle(lineColor, radius = 3.dp.toPx(), center = p)
                    }
                    // 每点下方标日期（只标最多 7 个避免拥挤）
                    // 文字用 drawContext 太复杂，这里简化为只画曲线，日期标签放到下方描述
                }
                // 命中率描述
                val recent = stats.takeLast(1).first()
                val avgRate = stats.filter { it.total > 0 }.let { list ->
                    val sum = list.sumOf { it.hits }
                    val tot = list.sumOf { it.total }
                    if (tot > 0) (sum * 100 / tot) else 0
                }
                Text(
                    "近${stats.size}天命中率约 $avgRate% · 最近(${recent.date}) ${if (recent.total > 0) recent.hits * 100 / recent.total else 0}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stats.joinToString("  ") { s -> "${s.date.takeLast(5)}:${s.hits}/${s.total}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun patternLabel(id: String): String = when (id) {
    "PREFIXED_CODE" -> "前缀匹配（取件码: XXX）"
    "THREE_SEGMENT_PARCEL" -> "三段式取件码（1-2-3456）"
    "FOUR_SEGMENT_PARCEL" -> "四段式取件码（A1-2-3-45）"
    "LETTER_TWO_SEGMENT_PARCEL" -> "两段式字母（A-1-234）"
    "LETTER_DASH_FIVE_PARCEL" -> "字母-数字（H-24137）"
    "LONG_NUMBER_PARCEL" -> "长数字（6-8位）"
    else -> id
}

@Composable
private fun LearnedRulesCard(
    rules: List<PatternLearner.LearnedRule>,
    onChanged: () -> Unit,
    onDelete: (PatternLearner.LearnedRule) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            IconText(R.drawable.ic_brain, "应用学到的识别方式", iconSize = 18.dp, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("应用会从识别情况中逐步适应新的取件码格式。你可以停用或删除不适用的方式。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (rules.isEmpty()) {
                Text(
                    "继续使用应用，它会逐步适应常见取件码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            rules.forEach { rule ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                rule.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (!rule.enabled || rule.decayed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                " · 命中 ${rule.count} 次",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (rule.decayed) {
                                AssistChip(onClick = {}, label = { Text("较少使用", style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface))
                            }
                        }
                        if (!rule.enabled) {
                            Text("已停用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    // A1: 停用/启用 + 删除 按钮 + 类型图标（Emoji 换成 Lucide 线性图标）
                    Image(
                        painter = painterResource(
                            when (rule.type) {
                                "pickup_food" -> R.drawable.ic_cup_soda
                                "coupon" -> R.drawable.ic_ticket
                                else -> R.drawable.ic_package
                            }
                        ),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(18.dp)
                    )
                    TextButton(
                        onClick = {
                            // 写 JSON 到 IO 线程，避免主线程同步 IO
                            scope.launch(Dispatchers.IO) {
                                PatternLearner.setRuleEnabled(context, rule.regex, !rule.enabled)
                                withContext(Dispatchers.Main) { onChanged() }
                            }
                        }
                    ) { Text(if (rule.enabled) "停用" else "启用", style = MaterialTheme.typography.labelSmall) }
                    TextButton(
                        onClick = { onDelete(rule) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}
