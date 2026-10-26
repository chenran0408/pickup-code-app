package com.pickupcode.app.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.HorizontalDivider
import com.pickupcode.app.ui.miuix.*
import com.pickupcode.app.ui.theme.UiSpacing
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pickupcode.app.R
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pickupcode.app.BuildConfig
import com.pickupcode.app.learner.PatternLearner
import com.pickupcode.app.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TAG = "SettingsScreen"

/** 各 section 共享的上下文，避免每个私有 Composable 传一堆参数 */
private data class SettingsCtx(
    val ctx: Context,
    val scope: CoroutineScope,
    val s: AppPreferences.Settings,
    val apiUrl: String,
    val apiKey: String,
    val apiModel: String,
    val amapApiKey: String,
    val kuaidi100Key: String,
    val onApiUrlChange: (String) -> Unit,
    val onApiKeyChange: (String) -> Unit,
    val onApiModelChange: (String) -> Unit,
    val onAmapApiKeyChange: (String) -> Unit,
    val onKuaidi100KeyChange: (String) -> Unit,
    val onSmsEnable: (Boolean) -> Unit
) {
    /** 开关保存：把 Boolean 回调包进 IO 协程，消除每个 SettingsSwitch 的 launch 样板 */
    fun save(block: suspend (Boolean) -> Unit): (Boolean) -> Unit = { value ->
        saveRun { block(value) }
    }

    /** 一次性保存：无参 suspend 块 + IO 调度（Slider/按钮/防抖字段用）；捕获写异常防崩溃 */
    fun saveRun(block: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) {
            runCatching { block() }
                .onFailure { Log.w(TAG, "设置写入失败", it) }
        }
    }
}

enum class SettingsPage(val title: String) {
    HOME("设置"), RECOGNITION_NOTIFICATIONS("识别与通知"), PICKUP_ASSIST("取件辅助"),
    DISPLAY("显示与体验"), DATA("数据与记录"), ADVANCED("高级功能"), ABOUT("关于"),
    // 保留旧状态名，避免系统从旧版保存状态恢复时 valueOf 失败。
    RECOGNITION("识别"), INPUT("通知与识别方式"), VERIFY("辅助服务"), APPEARANCE("显示")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    page: SettingsPage = SettingsPage.HOME,
    onBack: () -> Unit,
    onNavigatePage: (SettingsPage) -> Unit = {},
    onStatsClick: () -> Unit = {},
    onSavedAddressClick: () -> Unit = {},
    onRulesClick: () -> Unit = {},
    onNotificationAppsClick: () -> Unit = {},
    onDedupClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    onIdentityClick: () -> Unit = {},
    bottomNavigationBar: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    // 进程级 scope：防抖落盘/权限回调等 fire-and-forget 写不随页面销毁取消（否则 400ms 内返回会丢 Key/URL）
    val scope = com.pickupcode.app.App.appScope
    val settingsFlow = remember { AppPreferences.observe(ctx) }
    val s by settingsFlow.collectAsState(initial = AppPreferences.Settings())

    var apiUrl by remember { mutableStateOf(s.apiBaseUrl) }
    var apiKey by remember { mutableStateOf(s.apiKey) }
    var apiModel by remember { mutableStateOf(s.apiModel) }
    var amapApiKey by remember { mutableStateOf(s.amapApiKey) }
    var kuaidi100Key by remember { mutableStateOf(s.kuaidi100Key) }

    // 短信权限启动器：拒绝后开关自动回退
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch(Dispatchers.IO) { AppPreferences.setEnableSmsReceive(ctx, true) }
        } else {
            Toast.makeText(ctx, "短信权限被拒绝，无法自动识别取件码", Toast.LENGTH_SHORT).show()
        }
    }

    // DataStore 异步加载真实值后回填一次，避免已配置的 Key 在重启后显示为空（M1）
    LaunchedEffect(Unit) {
        AppPreferences.observe(ctx).first().let {
            apiUrl = it.apiBaseUrl; apiKey = it.apiKey; apiModel = it.apiModel
            amapApiKey = it.amapApiKey; kuaidi100Key = it.kuaidi100Key
        }
    }

    // remember 缓存：SettingsCtx 含函数字段（lambda），每次重建会导致 equals 恒不等，
    // 使全部 section 每次重组（滚动/动画卡顿的根源）。键用实际可变状态，状态未变时引用稳定。
    val sc = remember(s, apiUrl, apiKey, apiModel, amapApiKey, kuaidi100Key) {
        SettingsCtx(
            ctx = ctx, scope = scope, s = s,
            apiUrl = apiUrl, apiKey = apiKey, apiModel = apiModel,
            amapApiKey = amapApiKey, kuaidi100Key = kuaidi100Key,
            onApiUrlChange = { apiUrl = it },
            onApiKeyChange = { apiKey = it },
            onApiModelChange = { apiModel = it },
            onAmapApiKeyChange = { amapApiKey = it },
            onKuaidi100KeyChange = { kuaidi100Key = it },
            onSmsEnable = { enable ->
                if (enable) {
                    // 开启时先检查权限，未授权则发起请求；已授权直接保存
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        scope.launch(Dispatchers.IO) {
                            runCatching { AppPreferences.setEnableSmsReceive(ctx, true) }
                                .onFailure { Log.w(TAG, "短信开关保存失败", it) }
                        }
                    } else {
                        smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    }
                } else {
                    scope.launch(Dispatchers.IO) {
                        runCatching { AppPreferences.setEnableSmsReceive(ctx, false) }
                            .onFailure { Log.w(TAG, "短信开关保存失败", it) }
                    }
                }
            }
        )
    }

    top.yukonga.miuix.kmp.basic.Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        bottomBar = { if (page == SettingsPage.HOME) bottomNavigationBar?.invoke() },
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = page.title,
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(MiuixIcons.Back, "返回") }
                },
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(UiSpacing.page),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.section)
        ) {
            when (page) {
                SettingsPage.HOME -> SettingsHome(onNavigate = onNavigatePage)
                SettingsPage.RECOGNITION_NOTIFICATIONS, SettingsPage.RECOGNITION -> {
                    RecognitionSettingsSection(sc)
                    InputMethodsSection(sc, onNotificationAppsClick)
                    NotificationStatusCard(sc)
                    RulesSection(onRulesClick)
                }
                SettingsPage.PICKUP_ASSIST -> {
                    SavedAddressSection(onSavedAddressClick)
                    SettingsLinkRow("身份码平台", "打开淘宝、菜鸟或拼多多身份码", onIdentityClick)
                }
                SettingsPage.DISPLAY, SettingsPage.APPEARANCE -> AppearanceSection(sc)
                SettingsPage.DATA -> {
                    LearningStatsSection(sc, onStatsClick)
                    SettingsLinkRow("重复记录整理", "整理重复的取件记录", onDedupClick)
                    SettingsLinkRow("回收站", "查看、恢复或清理已删除的记录", onTrashClick)
                }
                SettingsPage.ADVANCED, SettingsPage.VERIFY -> VerifyServicesSection(sc)
                SettingsPage.ABOUT -> { RecognitionFeedbackSection(); AboutSection(sc) }
                SettingsPage.INPUT -> { InputMethodsSection(sc, onNotificationAppsClick); NotificationStatusCard(sc) }
            }
        }
    }
}

@Composable
private fun SettingsHome(onNavigate: (SettingsPage) -> Unit) {
    Text("管理识别方式、取件辅助和应用偏好", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SettingsCategoryRow("识别与通知", "自动识别、通知来源和识别规则") { onNavigate(SettingsPage.RECOGNITION_NOTIFICATIONS) }
        SettingsCategoryRow("取件辅助", "常用地址和身份码平台") { onNavigate(SettingsPage.PICKUP_ASSIST) }
        SettingsCategoryRow("显示与体验", "主题和文字大小") { onNavigate(SettingsPage.DISPLAY) }
        SettingsCategoryRow("数据与记录", "统计、重复整理和回收站") { onNavigate(SettingsPage.DATA) }
        SettingsCategoryRow("高级功能", "可选的联网辅助服务") { onNavigate(SettingsPage.ADVANCED) }
        SettingsCategoryRow("关于", "版本、隐私说明和应用信息") { onNavigate(SettingsPage.ABOUT) }
    }
}

@Composable
private fun SettingsCategoryRow(title: String, summary: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = UiSpacing.compact, vertical = UiSpacing.item),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun SettingsLinkRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = UiSpacing.compact, vertical = UiSpacing.item),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ═══════════════════ 设置分组：系统设置式标题 + 原生 Preference 行 ═══════════════════ */

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiSpacing.compact)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = UiSpacing.compact, top = UiSpacing.item))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = UiSpacing.compact))
        }
        content()
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    sub: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    @DrawableRes icon: Int? = null
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onChange,
        title = title,
        summary = sub,
        modifier = Modifier.fillMaxWidth(),
        startAction = icon?.let { iconRes ->
            {
                androidx.compose.foundation.Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary),
                )
            }
        },
    )
}

/* ═══════════════════ 各分组 Section ═══════════════════ */

@Composable
private fun SettingsSubHeader(text: String, @DrawableRes @Suppress("UNUSED_PARAMETER") icon: Int? = null) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = UiSpacing.compact, top = UiSpacing.item))
}

/** ① 识别设置：灵敏度 + 识别类型 */
@Composable
private fun RecognitionSettingsSection(sc: SettingsCtx) {
    SettingsSectionCard(title = "识别范围", subtitle = "选择码的类型，调整识别灵敏度") {
        var confDraft by remember(sc.s.confidenceThreshold) { mutableStateOf(sc.s.confidenceThreshold) }
        SliderPreference(
            title = "识别灵敏度",
            summary = "调节识别阈值",
            valueText = "${(confDraft * 100).roundToInt()}%",
            value = confDraft,
            onValueChange = { confDraft = it },
            onValueChangeFinished = { sc.saveRun { AppPreferences.setConfidenceThreshold(sc.ctx, confDraft) } },
            valueRange = 0.1f..0.8f,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsSubHeader("识别类型")
        SettingsSwitch("取餐码", icon = R.drawable.ic_cup_soda, checked = sc.s.enableFoodCodes, onChange = sc.save { AppPreferences.setEnableFood(sc.ctx, it) })
        SettingsSwitch("取件码", icon = R.drawable.ic_package, checked = sc.s.enableParcelCodes, onChange = sc.save { AppPreferences.setEnableParcel(sc.ctx, it) })
        SettingsSwitch(
            "券码", icon = R.drawable.ic_ticket,
            sub = "识别屏幕/图片中的二维码（解码内容为码值；识别到则只标券码，不叠加取餐/取件码）",
            checked = sc.s.enableCouponCodes,
            onChange = sc.save { AppPreferences.setEnableCoupon(sc.ctx, it) }
        )
    }
}

/** ② 输入方式：无障碍 + 外部接收 + 短信 */
@Composable
private fun InputMethodsSection(sc: SettingsCtx, onNotificationAppsClick: () -> Unit) {
    SettingsSectionCard(title = "识别方式", subtitle = "选择短信、截图等识别入口") {
        SettingsSubHeader("无障碍服务")
        OutlinedButton(
            onClick = { sc.saveRun { AppPreferences.setHideAccessibilityCard(sc.ctx, false) } },
            enabled = sc.s.hideAccessibilityCard,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) { Text("在主页重新显示无障碍提示") }
        SettingsSubHeader("外部接收")
        SettingsSwitch("Intent 接收", icon = R.drawable.ic_link, sub = "接收来自其他App的分享（文本/图片）", checked = sc.s.enableIntentReceive, onChange = sc.save { AppPreferences.setEnableIntentReceive(sc.ctx, it) })
        SettingsSwitch("分享识别", icon = R.drawable.ic_upload, sub = "文本选择菜单/拖放直达时自动识别取餐取件码", checked = sc.s.enableShareDetection, onChange = sc.save { AppPreferences.setEnableShareDetection(sc.ctx, it) })
        SettingsSubHeader("短信与通知识别")
        SmsNotificationSettings(sc, onNotificationAppsClick)
        SettingsSwitch(
            "短信取件码自动识别", icon = R.drawable.ic_send,
            sub = if (sc.s.enableSmsReceive) "已开启" else "已关闭",
            checked = sc.s.enableSmsReceive,
            onChange = sc.onSmsEnable
        )
        SettingsSubHeader("到期提醒", icon = R.drawable.ic_hourglass)
        SettingsSwitch(
            "快递取件码到期提醒",
            sub = if (sc.s.enableExpiryRemind) "按文字时限或免费保管期限提醒；未注明时默认 3 天" else "已关闭",
            checked = sc.s.enableExpiryRemind,
            onChange = sc.save { AppPreferences.setEnableExpiryRemind(sc.ctx, it) }
        )
    }
}

/* ═══════════════════ 通知状态（各 ROM 通知管理适配） ═══════════════════ */

private enum class NotifStatus { OK, NEED_PERMISSION, DISABLED, CHANNEL_SILENT }

/**
 * 三态检测：① Android 13+ 通知权限 ② 系统「通知管理」总开关（vivo/小米等 ROM 独立于 13 权限）
 * ③ 各渠道是否被单独静默（importance == NONE）。
 * 返回 (状态, 说明文案)。正常时返回 OK。
 */
@Composable
private fun SmsNotificationSettings(sc: SettingsCtx, onNotificationAppsClick: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val listenerState by com.pickupcode.app.service.NotificationRecognitionStatus.state.collectAsState()
    var granted by remember { mutableStateOf(com.pickupcode.app.service.SmsNotificationListener.hasAccess(sc.ctx)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = com.pickupcode.app.service.SmsNotificationListener.hasAccess(sc.ctx)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun openAccess() { com.pickupcode.app.service.SmsNotificationListener.openAccess(sc.ctx) }
    TextButton(onClick = onNotificationAppsClick) {
        Text("选择通知来源（${sc.s.notificationApps.size}）")
    }
    Text(com.pickupcode.app.service.NotificationRecognitionStatus.description(sc.s.notificationApps.size, granted, listenerState),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (granted && !listenerState.connected && sc.s.notificationApps.isNotEmpty()) TextButton(onClick = {
        com.pickupcode.app.service.NotificationListenerConnection.recover(sc.ctx)
    }, enabled = listenerState.connection != com.pickupcode.app.service.NotificationRecognitionStatus.Connection.CONNECTING) { Text("重新连接") }
    if (sc.s.notificationApps.isNotEmpty()) TextButton(onClick = { openAccess() }) {
        Text(if (granted) "管理通知访问权限" else "开启通知访问权限")
    }
}

private fun computeNotificationStatus(ctx: Context): Pair<NotifStatus, String> {
    val nm = ctx.getSystemService(NotificationManager::class.java)
        ?: return NotifStatus.OK to ""
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return NotifStatus.NEED_PERMISSION to "Android 13+ 需要「通知」权限，未授权时所有通知都不会显示"
    }
    // areNotificationsEnabled() 反映系统通知管理总开关（含 ROM 层），不只 Android 13 权限
    if (!nm.areNotificationsEnabled()) {
        return NotifStatus.DISABLED to "系统「通知管理」里本 App 的通知被整体关闭，识别结果不会提醒"
    }
    val silentChannels = listOf("pickup_food", "pickup_parcel", "pickup_coupon")
        .mapNotNull { nm.getNotificationChannel(it) }
        .filter { it.importance == NotificationManager.IMPORTANCE_NONE }
    if (silentChannels.isNotEmpty()) {
        return NotifStatus.CHANNEL_SILENT to
            "以下渠道被设为静默，通知可能不显示：${silentChannels.joinToString("、") { it.name }}"
    }
    return NotifStatus.OK to ""
}

private fun openNotificationSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        )
    } catch (_: Exception) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            )
        } catch (_: Exception) {
            Toast.makeText(ctx, "无法打开通知设置", Toast.LENGTH_SHORT).show()
        }
    }
}

/** 通知状态卡片：有问题才显示（正常时不占版面）；从系统设置返回时自动刷新。 */
@Composable
private fun NotificationStatusCard(sc: SettingsCtx) {
    val ctx = sc.ctx
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }
    // 从系统「通知设置」页返回后重算（用户可能刚去开了开关）
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val (status, desc) = remember(refreshTick) { computeNotificationStatus(ctx) }
    if (status == NotifStatus.OK) return

    val isError = status == NotifStatus.DISABLED
    val container = if (isError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.tertiaryContainer
    val onContainer = if (isError) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onTertiaryContainer
    val title = when (status) {
        NotifStatus.NEED_PERMISSION -> "需要通知权限"
        NotifStatus.DISABLED -> "系统通知被关闭"
        else -> "部分通知渠道被静默"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = onContainer)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { openNotificationSettings(ctx) }) { Text("去开启") }
        }
    }
}

@Composable
private fun RulesSection(onRulesClick: () -> Unit) {
    SettingsSectionCard(
        title = "识别规则",
        subtitle = "添加或调整识别规则，可随时恢复默认"
    ) {
        OutlinedButton(
            onClick = onRulesClick,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) { Text("管理识别规则 →") }
    }
}

@Composable
private fun LearningStatsSection(sc: SettingsCtx, onStatsClick: () -> Unit) {
    SettingsSectionCard(title = "数据统计") {
        OutlinedButton(
            onClick = onStatsClick,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) { Text("查看详细统计 →") }
        LearningStatsPanel(sc.ctx, sc.scope)
    }
}

@Composable
private fun VerifyServicesSection(sc: SettingsCtx) {
    // 「测试 AI 连接」的自检状态（诊断用：把"配了 Key 却没作用"变成一条能看懂的结果）
    var probing by remember { mutableStateOf(false) }
    var probeText by remember { mutableStateOf<String?>(null) }
    var probeOk by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()

    SettingsSectionCard(title = "辅助识别", subtitle = "用 AI、地图或快递服务补充识别（可选，需联网）") {
        SettingsSubHeader("地图验证", icon = R.drawable.ic_map)
        SettingsSwitch(
            "启用地图验证",
            sub = if (sc.s.enableMapVerify) "已启用" else "已关闭（隐私优先）",
            checked = sc.s.enableMapVerify,
            onChange = sc.save { AppPreferences.setEnableMapVerify(sc.ctx, it) }
        )
        if (sc.s.enableMapVerify) {
            Text("系统会优先使用本机定位信息。想提高地址准确度时，可填写高德地图服务密钥：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            DebouncedKeyField(
                value = sc.amapApiKey, label = "高德地图服务密钥（可选）",
                onCommit = { sc.saveRun { AppPreferences.setAmapApiKey(sc.ctx, it) } },
                onChange = sc.onAmapApiKeyChange
            )
            AmapHelpSection()
        }
        SettingsSubHeader("快递100验证", icon = R.drawable.ic_mailbox)
        SettingsSwitch(
            "启用快递100验证",
            sub = if (sc.s.enableKuaidi100) "已启用" else "已关闭",
            checked = sc.s.enableKuaidi100,
            onChange = sc.save { AppPreferences.setEnableKuaidi100(sc.ctx, it) }
        )
        if (sc.s.enableKuaidi100) {
            DebouncedKeyField(
                value = sc.kuaidi100Key, label = "快递100查询密钥",
                onCommit = { sc.saveRun { AppPreferences.setKuaidi100Key(sc.ctx, it) } },
                onChange = sc.onKuaidi100KeyChange
            )
            Kuaidi100HelpSection()
        }
        SettingsSubHeader("AI 识别", icon = R.drawable.ic_bot)
        SettingsSwitch(
            "启用 AI 识别",
            sub = if (sc.s.enableAI) {
                if (sc.s.apiKey.isNotBlank()) "已开启" else "需要填写服务密钥"
            } else "已关闭",
            checked = sc.s.enableAI,
            onChange = sc.save { AppPreferences.setEnableAI(sc.ctx, it) }
        )
        if (sc.s.enableAI) {
            DebouncedKeyField(
                value = sc.apiUrl, label = "AI 服务地址",
                onCommit = { sc.saveRun { AppPreferences.setApiBaseUrl(sc.ctx, it) } },
                onChange = sc.onApiUrlChange
            )
            DebouncedKeyField(
                value = sc.apiKey, label = "AI 服务密钥", isPassword = true,
                onCommit = { sc.saveRun { AppPreferences.setApiKey(sc.ctx, it) } },
                onChange = sc.onApiKeyChange
            )
            DebouncedKeyField(
                value = sc.apiModel, label = "模型名称",
                onCommit = { sc.saveRun { AppPreferences.setApiModel(sc.ctx, it) } },
                onChange = sc.onApiModelChange
            )
            SettingsSwitch(
                "让 AI 识别图片",
                sub = if (sc.s.enableAiImage) "已开启：截图会发送到你配置的 AI 服务，可能包含姓名或电话"
                else "已关闭：只发送已识别出的文字",
                checked = sc.s.enableAiImage,
                onChange = sc.save { AppPreferences.setEnableAiImage(sc.ctx, it) }
            )
            if (sc.s.enableAiImage) {
                Text(
                    "开启后，整张截图会发送到你配置的 AI 服务，可能包含姓名、电话等个人信息。请确认你信任该服务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            // 2026-09-17 真机排障：用户把 Key/地址/模型都填好了，却忘了打开上面的开关，
            // 而字段又只在开关打开时才显示 → 看起来"配置好了却没作用"。这里给出明确提示。
            Text(
                if (sc.s.apiKey.isNotBlank())
                    "已填写 AI 服务密钥，但上方开关已关闭。打开后才会使用智能识别。"
                else "智能识别已关闭。填写服务地址、密钥和模型名称后，再打开上方开关。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // —— 诊断：AI 连接自检（**无论开关是否打开都显示**，避免"配了却没开开关"无从判断）——
        // 设计目的：AI 只做"补漏"（正则识别到的码会被去重丢弃），失败也只写日志，
        // 于是"配好了却没作用"很难判断。这里直接用当前 URL/Key/模型发一次最小请求，
        // 把 HTTP 状态码、错误正文、耗时、**实际请求地址**都显示出来。
        Text(
            "AI 用来补充常规识别没有找到的取件码，不会重复添加已找到的记录。你可以用下方按钮检查连接。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "请按 AI 服务提供商给出的地址、密钥和模型名称填写。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            enabled = !probing,
            onClick = {
                probing = true
                probeText = null
                uiScope.launch {
                    val t = com.pickupcode.app.extractor.AIExtractor.probe(sc.apiKey, sc.apiUrl, sc.apiModel)
                    var imgOk = true
                    if (sc.s.enableAiImage) {
                        val probeImg = com.pickupcode.app.util.ImageUtils.makeProbeImageBase64()
                        val img = probeImg?.let {
                            com.pickupcode.app.extractor.AIExtractor.probe(sc.apiKey, sc.apiUrl, sc.apiModel, imageBase64 = it)
                        }
                        imgOk = img?.ok == true
                    }
                    probing = false
                    probeOk = t.ok && imgOk
                    probeText = if (probeOk) "连接正常，可以使用智能识别。" else
                        "连接失败。请检查服务地址、密钥和模型名称后重试。"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) { Text(if (probing) "正在检查…" else "检查智能识别连接") }
        probeText?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (probeOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AppearanceSection(sc: SettingsCtx) {
    SettingsSectionCard(title = "外观") {
        SettingsSwitch("大字模式", sub = "文字更大，按钮更好点",
            checked = sc.s.largeText, onChange = sc.save { AppPreferences.setLargeText(sc.ctx, it) })
        val themes = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
        Column {
            themes.forEach { (value, label) ->
                RadioButtonPreference(
                    title = label,
                    selected = sc.s.darkMode == value,
                    onClick = { sc.saveRun { AppPreferences.setDarkMode(sc.ctx, value) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 常用取件地址（预存地址）入口。
 * 与自动学习的"常用站点"分开：这里进的是用户手动录入的权威地址列表。
 */
@Composable
private fun SavedAddressSection(onClick: () -> Unit) {
    SettingsSectionCard(title = "常用取件地址") {
        Column {
            Text(
                "保存常去的驿站或快递柜，识别时优先显示你填写的地址。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "管理常用地址 ›",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onClick() }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AboutSection(sc: SettingsCtx) {    SettingsSectionCard(title = "关于") {
        Column {
            Text("码上闪记 v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            Text("文字识别在设备上完成 · 取件记录保存在本机",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            val uriHandler = LocalUriHandler.current
            Text(
                "GitHub: https://github.com/chenran0408/pickup-code-app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { uriHandler.openUri("https://github.com/chenran0408/pickup-code-app") }
                    .padding(vertical = 2.dp)
            )
            // 识别调试面板：仅 DEBUG 构建显示（生产裁剪）
            if (BuildConfig.DEBUG) {
                var showDebug by remember { mutableStateOf(false) }
                Text(
                    "识别调试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { showDebug = true }
                        .padding(vertical = 2.dp)
                )
                if (showDebug) {
                    RecognitionDebugDialog(onDismiss = { showDebug = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        var upStatus by remember { mutableStateOf<String?>(null) }
        var checking by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = {
                checking = true
                sc.scope.launch {
                    val result = checkUpdate()
                    upStatus = result
                    if (result.startsWith("检查失败")) Toast.makeText(sc.ctx, result, Toast.LENGTH_SHORT).show()
                    checking = false
                }
            },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) { Text(if (checking) "检查中..." else "检查更新") }
        upStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ═══════════════════ 原辅助组件（保留） ═══════════════════ */

@Composable
private fun LearningStatsPanel(ctx: Context, scope: CoroutineScope) {
    var stats by remember { mutableStateOf<PatternLearner.PatternStats?>(null) }
    var addrStats by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var suggestions by remember { mutableStateOf<List<PatternLearner.PatternSuggestion>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            stats = PatternLearner.getStats(ctx)
            addrStats = PatternLearner.getAddressStats(ctx)
            suggestions = PatternLearner.getSuggestions(ctx)
        }
    }

    val s = stats
    if (s == null || s.totalScans == 0) {
        Text("暂无识别数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val hitRate = if (s.attempts > 0) (s.attempts.toFloat() / s.totalScans * 100).roundToInt() else 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("检查 ${s.totalScans} 次 · 找到取件码 ${s.attempts} 次（${hitRate}%）· 未找到 ${s.misses} 次", style = MaterialTheme.typography.bodyMedium)
        if (s.verified > 0) {
            Text("已确认 ${s.verified} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val (addrOk, addrTotal) = addrStats ?: (0 to 0)
        if (addrTotal > 0) {
            val addrRate = (addrOk.toFloat() / addrTotal * 100).roundToInt()
            Text("地址验证：$addrOk / $addrTotal（${addrRate}%）", style = MaterialTheme.typography.bodySmall)
        }

        if (suggestions.isNotEmpty()) {
            Text("待改进的取件码格式：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            for (sg in suggestions.take(3)) {
                Text("  ${sg.label} — 遇到 ${sg.count} 次", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text("  例如：${sg.sampleCodes.joinToString("，")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        TextButton(
            onClick = { confirmClear = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) { Text("清除未识别内容", style = MaterialTheme.typography.labelSmall) }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除未识别内容？") },
            text = { Text("这会删除尚未识别出的内容和相关学习记录，无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch(Dispatchers.IO) {
                        PatternLearner.clearUnmatched(ctx)
                        suggestions = PatternLearner.getSuggestions(ctx)
                    }
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun DebouncedKeyField(
    value: String,
    label: String,
    onCommit: (String) -> Unit,
    onChange: (String) -> Unit,
    isPassword: Boolean = false,
    debounceMs: Long = 400
) {
    // 以 value 作 key：外部回填（如 DataStore 异步加载后）变化时重置内部 text，
    // 否则已配置的 Key/URL 在重启后显示为空/默认值（M1 回归）。
    var text by remember(value) { mutableStateOf(value) }
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 用 remember 持有 Job，避免重组时重置为 null 导致防抖失效（H5）
    val saveJob = remember { mutableStateOf<Job?>(null) }
    // 离开页面时若仍有未提交的防抖输入，立即落盘，避免 400ms 内返回导致 Key/URL 静默丢失
    val latestText = remember { mutableStateOf(text) }
    latestText.value = text
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            saveJob.value?.cancel()
            // 仅当与外部 value 不同才提交，避免无改动的多余写
            if (latestText.value != value) {
                onCommit(latestText.value)
            }
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
            saveJob.value?.cancel()
            saveJob.value = scope.launch {
                delay(debounceMs)
                onCommit(text)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        labelText = label,
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                TextButton(onClick = { visible = !visible },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(if (visible) "隐藏" else "显示", style = MaterialTheme.typography.labelSmall)
                }
            }
        } else null
    )
}

@Composable
private fun AmapHelpSection() {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起说明 ▲" else "如何获取高德地图服务密钥？▼", style = MaterialTheme.typography.labelMedium)
        }
        if (expanded) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1. 打开浏览器访问", style = MaterialTheme.typography.bodySmall)
                    Text("   https://console.amap.com/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text("2. 注册/登录高德开放平台账号", style = MaterialTheme.typography.bodySmall)
                    Text("3. 进入「应用管理 → 我的应用」→ 创建应用", style = MaterialTheme.typography.bodySmall)
                    Text("4. 为应用添加 Key，服务平台选择「Web服务」", style = MaterialTheme.typography.bodySmall)
                    Text("5. 复制生成的 Key 粘贴到上方输入框", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("免费额度：每日 3000 次地理编码，个人使用完全够用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Kuaidi100HelpSection() {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起说明 ▲" else "如何获取快递100查询密钥？▼", style = MaterialTheme.typography.labelMedium)
        }
        if (expanded) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1. 打开浏览器访问", style = MaterialTheme.typography.bodySmall)
                    Text("   https://api.kuaidi100.com/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text("2. 注册/登录快递100开放平台", style = MaterialTheme.typography.bodySmall)
                    Text("3. 进入「企业管理 → 我的授权Key」", style = MaterialTheme.typography.bodySmall)
                    Text("4. 复制 Customer Key 粘贴到上方输入框", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("识别出快递单号后，可查询对应取件码和地址，帮助检查识别是否准确。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("查询结果可帮助应用逐步改进识别效果。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private suspend fun checkUpdate(): String = withContext(Dispatchers.IO) {
    var resp: java.net.HttpURLConnection? = null
    try {
        resp = java.net.URL("https://api.github.com/repos/chenran0408/pickup-code-app/releases/latest")
            .openConnection() as java.net.HttpURLConnection
        resp.requestMethod = "GET"; resp.setRequestProperty("Accept", "application/vnd.github.v3+json")
        resp.setRequestProperty("User-Agent", "pickup-code-app-checkupdate")
        resp.connectTimeout = 10000; resp.readTimeout = 10000
        if (resp.responseCode != 200) return@withContext "检查失败"
        val latest = org.json.JSONObject(resp.inputStream.bufferedReader().use { it.readText() }).getString("tag_name").removePrefix("v")
        if (latest == BuildConfig.VERSION_NAME) "当前已是最新版本" else "发现新版本 v$latest"
    } catch (_: Exception) { "检查失败，请稍后重试" }
    finally { resp?.disconnect() }
}
