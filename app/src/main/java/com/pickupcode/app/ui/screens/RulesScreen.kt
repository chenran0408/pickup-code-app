@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pickupcode.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Delete
import com.pickupcode.app.ui.miuix.AlertDialog
import com.pickupcode.app.ui.miuix.Card
import androidx.compose.material3.CardDefaults
import com.pickupcode.app.ui.miuix.FilterChip
import com.pickupcode.app.ui.miuix.Icon
import com.pickupcode.app.ui.miuix.IconButton
import com.pickupcode.app.ui.miuix.DropdownMenu
import com.pickupcode.app.ui.miuix.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import com.pickupcode.app.ui.miuix.OutlinedTextField
import com.pickupcode.app.ui.miuix.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import com.pickupcode.app.ui.miuix.Switch
import com.pickupcode.app.ui.miuix.Text
import com.pickupcode.app.ui.miuix.TextButton
import com.pickupcode.app.ui.miuix.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pickupcode.app.R
import com.pickupcode.app.extractor.CodeExtractor
import com.pickupcode.app.learner.PatternLearner
import com.pickupcode.app.ui.components.IconText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「识别规则」管理页。
 *
 * 这个页面回答两个问题：
 *  1. **现在有哪些正则在跑？** —— 内置模式（出厂定义）+ 我的规则（自动学习 + 手动添加）
 *  2. **改坏了怎么办？** —— 每条可单独还原，整页可一键还原默认
 *
 * 关键设计（用户要求：学习规则与手动添加不冲突、共用同一管线）：
 *  - 「我的规则」里自动学习和手动添加**混在同一个列表**，只是标注来源不同；它们在识别管线里完全等价。
 *  - 内置正则不进用户存储，而是「覆盖」：只记停用与改写，出厂定义始终在代码里
 *    （CodeExtractor.BUILTIN_RULES）→ 一键还原＝丢掉覆盖，天然可回退。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var builtins by remember { mutableStateOf<List<CodeExtractor.BuiltinRuleInfo>>(emptyList()) }
    var myRules by remember { mutableStateOf<List<PatternLearner.LearnedRule>>(emptyList()) }
    // 编辑弹窗状态：null=不显示；otherwise (编辑对象, 是内置模式吗)
    var editingBuiltin by remember { mutableStateOf<CodeExtractor.BuiltinRuleInfo?>(null) }
    var editingRule by remember { mutableStateOf<PatternLearner.LearnedRule?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    suspend fun reload() {
        val (b, r) = withContext(Dispatchers.IO) {
            CodeExtractor.builtinRuleInfos(context) to PatternLearner.getLearnedPatterns(context)
        }
        builtins = b
        myRules = r
    }

    LaunchedEffect(Unit) { reload() }

    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "识别规则",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { adding = true }) { Icon(MiuixIcons.Add, contentDescription = "新增规则") }
                },
            )
        },
        snackbarHost = { SnackbarHost(state = snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "这些识别方式决定应用如何从通知和页面内容中找到取件码。你可以开启、停用或调整；需要时也能恢复默认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------------- 内置正则模式 ----------------
            item {
                IconText(
                    R.drawable.ic_wrench, "默认识别方式（已开启 ${builtins.count { it.enabled }} 项）",
                    iconSize = 18.dp, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
            }
            items(builtins, key = { "b_" + it.id }, contentType = { "builtin" }) { info ->
                BuiltinRuleRow(
                    info = info,
                    onToggle = { enabled ->
                        scope.launch {
                            withContext(Dispatchers.IO) { PatternLearner.setBuiltinEnabled(context, info.id, enabled) }
                            reload()
                        }
                    },
                    onEdit = { editingBuiltin = info },
                    onReset = {
                        scope.launch {
                            withContext(Dispatchers.IO) { PatternLearner.resetBuiltin(context, info.id) }
                            reload()
                            toast("已还原「${info.label}」")
                        }
                    }
                )
            }

            // ---------------- 我的规则 ----------------
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconText(
                        R.drawable.ic_brain, "我的识别方式（${myRules.size}）",
                        modifier = Modifier.weight(1f), iconSize = 18.dp, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "你添加的方式和应用逐步学到的方式都在这里管理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (myRules.isEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            "还没有自定义识别方式。你可以添加一种，也可以继续使用应用，让它逐步适应常见取件码。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            items(myRules, key = { "r_" + it.regex }, contentType = { "myrule" }) { rule ->
                MyRuleRow(
                    rule = rule,
                    onToggle = { enabled ->
                        scope.launch {
                            withContext(Dispatchers.IO) { PatternLearner.setRuleEnabled(context, rule.regex, enabled) }
                            reload()
                        }
                    },
                    onEdit = { editingRule = rule },
                    onDelete = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { PatternLearner.deleteRule(context, rule.regex) }
                                reload()
                                toast("已删除规则")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                toast("删除失败，请重试")
                            }
                        }
                    }
                )
            }

            // ---------------- 一键还原 ----------------
            item {
                Spacer(Modifier.height(4.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        IconText(
                            R.drawable.ic_refresh_cw, "还原默认规则",
                            iconSize = 18.dp,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                        "恢复默认识别规则，并删除你添加或学到的规则。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { confirmReset = true },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("一键还原默认") }
                    }
                }
            }
        }
    }

    // ---------------- 弹窗 ----------------
    editingBuiltin?.let { info ->
        RegexEditDialog(
            title = "调整识别格式",
            subtitle = info.label,
            initialRegex = info.regex,
            initialLabel = null,
            initialType = null,
            showRuleFields = false,
            onDismiss = { editingBuiltin = null },
            onSave = { regex, _, _ ->
                scope.launch {
                    val check = PatternLearner.validateRegex(regex)
                    if (!check.ok) {
                        toast(humanRuleValidation(check.error ?: ""))
                    } else {
                        withContext(Dispatchers.IO) { PatternLearner.setBuiltinRegex(context, info.id, regex) }
                        editingBuiltin = null
                        reload()
                        toast("已更新「${info.label}」")
                    }
                }
            }
        )
    }

    if (adding || editingRule != null) {
        val target = editingRule
        RegexEditDialog(
            title = if (target == null) "添加规则" else "编辑规则",
            subtitle = if (target == null) "符合此格式的内容会被当作取件码" else target.label,
            initialRegex = target?.regex ?: "",
            initialLabel = target?.label,
            initialType = target?.type,
            showRuleFields = true,
            onDismiss = { adding = false; editingRule = null },
            onSave = { regex, type, label ->
                scope.launch {
                    val check = PatternLearner.validateRegex(regex)
                    if (!check.ok) {
                        toast(humanRuleValidation(check.error ?: ""))
                        return@launch
                    }
                    if (check.warning != null) toast(humanRuleValidation(check.warning))
                    val result = withContext(Dispatchers.IO) {
                        if (target == null) PatternLearner.addUserRule(context, regex, type, label)
                        else PatternLearner.updateRule(context, target.regex, regex, type, label)
                    }
                    if (result.isSuccess) {
                        adding = false; editingRule = null
                        reload()
                        toast(if (target == null) "已添加规则" else "已保存修改")
                    } else {
                        toast(result.exceptionOrNull()?.message ?: "保存失败")
                    }
                }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("还原默认规则？") },
            text = {
                    Text("恢复默认识别规则，并删除 ${myRules.size} 条自定义规则。此操作无法撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val (rules, _) = withContext(Dispatchers.IO) {
                            PatternLearner.restoreDefaults(context)
                        }
                        confirmReset = false
                        reload()
                        toast("已恢复默认识别方式，移除了 $rules 条自定义方式")
                    }
                }) { Text("确认还原", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } }
        )
    }

}

/* ═══════════════════ 行 ═══════════════════ */

@Composable
private fun BuiltinRuleRow(
    info: CodeExtractor.BuiltinRuleInfo,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit
) {
    var menu by remember(info.id) { mutableStateOf(false) }
    var showDetails by remember(info.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(info.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = if (info.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(builtinPurpose(info.label, info.type), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${ruleTypeLabel(info.type)} · ${if (info.enabled) "启用中" else "已停用"}${if (!info.editable) " · 固定方式" else ""}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (info.overridden) Text(if (info.overrideRejected) "格式无效，已恢复原设置" else "已调整",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            TypeIcon(info.type)
            Switch(checked = info.enabled, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menu = true }) { Icon(MiuixIcons.More, "${info.label}更多操作") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("规则详情") }, onClick = { menu = false; showDetails = true })
                    if (info.editable) DropdownMenuItem(text = { Text("改写规则") }, onClick = { menu = false; onEdit() })
                    if (info.overridden || !info.enabled) DropdownMenuItem(text = { Text("还原此规则") }, onClick = { menu = false; onReset() })
                }
            }
        }
    }
    if (showDetails) AlertDialog(
        onDismissRequest = { showDetails = false },
        title = { Text(info.label) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(builtinPurpose(info.label, info.type))
            Text("识别格式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(info.regex, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        } },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text("关闭") } },
    )
}

@Composable
private fun MyRuleRow(
    rule: PatternLearner.LearnedRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember(rule.regex) { mutableStateOf(false) }
    var showDetails by remember(rule.regex) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(rule.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = if (rule.enabled && rule.badCount < 3) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(learnedPurpose(rule.type, rule.source), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${if (rule.enabled && rule.badCount < 3) "启用中" else "已停用"} · 成功识别 ${rule.count} 次",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (rule.badCount >= 3 || rule.decayed) Text(if (rule.badCount >= 3) "多次标记有误，已自动停用" else "近期较少使用",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            TypeIcon(rule.type)
            Switch(checked = rule.enabled && rule.badCount < 3, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menu = true }) { Icon(MiuixIcons.More, "${rule.label}更多操作") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("规则详情") }, onClick = { menu = false; showDetails = true })
                    DropdownMenuItem(text = { Text("编辑规则") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("删除规则", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() },
                        leadingIcon = { Icon(MiuixIcons.Delete, null, tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
    }
    if (showDetails) AlertDialog(
        onDismissRequest = { showDetails = false },
        title = { Text(rule.label) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(learnedPurpose(rule.type, rule.source))
            Text("识别格式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rule.regex, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        } },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text("关闭") } },
    )
}

private fun ruleTypeLabel(type: String): String = when (type) {
    "pickup_food" -> "取餐码识别"
    "coupon" -> "券码识别"
    else -> "取件码识别"
}

private fun humanRuleValidation(message: String): String = when {
    message.contains("不能为空") -> "请填写识别格式。"
    message.contains("过长") -> "内容太长，请精简后重试。"
    message.contains("语法错误") -> "格式有误，请检查输入。"
    message.contains("几乎所有文本") -> "这个格式范围太宽，可能把普通内容误认为取件码，请补充更具体的条件。"
    message.contains("空字符串") -> "这个格式可能识别出空结果，请调整后重试。"
    message.contains("边界") -> "建议限制格式前后的字符，避免从更长的号码中截取一部分。"
    else -> message.replace("正则", "识别格式").replace("候选", "结果")
}

private fun builtinPurpose(label: String, type: String): String = when {
    type == "coupon" || label.contains("券号") -> "从优惠券通知中提取符合此格式的券号。"
    label.contains("取餐") || type == "pickup_food" -> "识别取餐通知中符合此格式的取餐码。"
    label.contains("前缀") || label.contains("标签") || label.contains("凭码") -> "结合取件提示文字识别，降低把普通数字误当取件码的概率。"
    else -> "识别符合此数字或字母结构的取件码候选值。"
}

private fun learnedPurpose(type: String, source: String): String {
    val kind = ruleTypeLabel(type)
    val origin = when (source) {
        PatternLearner.SOURCE_USER -> "你手动添加的规则"
        PatternLearner.SOURCE_VERIFIED -> "根据你确认过的记录学习到的规则"
        else -> "应用根据过往识别情况整理的方式"
    }
    return "$origin，用于$kind。"
}

@Composable
private fun TypeIcon(type: String) {
    Image(
        painter = painterResource(
            when (type) {
                "pickup_food" -> R.drawable.ic_cup_soda
                "coupon" -> R.drawable.ic_ticket
                else -> R.drawable.ic_package
            }
        ),
        contentDescription = null,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.size(18.dp)
    )
}

/* ═══════════════════ 正则编辑弹窗（新增/编辑规则 & 改写内置） ═══════════════════ */

@Composable
private fun RegexEditDialog(
    title: String,
    subtitle: String,
    initialRegex: String,
    initialLabel: String?,
    initialType: String?,
    /** 是否显示「名称 / 类型」字段：新增或编辑自定义规则=true；改写内置正则=false（只有正则一个字段）。 */
    showRuleFields: Boolean,
    onDismiss: () -> Unit,
    onSave: (regex: String, type: String, label: String) -> Unit
) {
    var regex by remember { mutableStateOf(initialRegex) }
    var label by remember { mutableStateOf(initialLabel ?: "") }
    var type by remember { mutableStateOf(initialType ?: "pickup_parcel") }

    // 实时校验：让用户在按保存之前就看到问题（而不是保存后才弹错误）
    val check = remember(regex) {
        if (regex.isBlank()) null else PatternLearner.validateRegex(regex)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = regex,
                    onValueChange = { regex = it },
                    labelText = "识别格式",
                    singleLine = false,
                    maxLines = 4,
                    isError = check?.ok == false,
                    supportingText = {
                        when {
                            check == null -> Text("符合此格式的内容会被识别为取件码",
                                style = MaterialTheme.typography.labelSmall)
                            check.ok.not() -> Text(humanRuleValidation(check.error ?: ""), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                            check.warning != null -> Text(humanRuleValidation(check.warning), style = MaterialTheme.typography.labelSmall,
                                color = com.pickupcode.app.ui.theme.TypeCoupon)
                            else -> Text("看起来没问题", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (showRuleFields) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        labelText = "名称（可选）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("类型", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("pickup_parcel" to "取件码", "pickup_food" to "取餐码", "coupon" to "券码").forEach { (v, t) ->
                            FilterChip(selected = type == v, onClick = { type = v }, label = { Text(t) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = regex.isNotBlank() && check?.ok != false,
                onClick = { onSave(regex, type, label) }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
