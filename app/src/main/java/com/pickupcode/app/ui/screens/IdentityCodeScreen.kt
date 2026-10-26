package com.pickupcode.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import com.pickupcode.app.ui.miuix.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.pickupcode.app.ui.theme.UiSpacing
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pickupcode.app.R
import com.pickupcode.app.ui.components.IconText
import com.pickupcode.app.ui.components.BrandBadge
import com.pickupcode.app.util.IdentityCodeLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「点开身份码」页：把用户送去 淘宝 / 菜鸟 / 拼多多 的身份码页面。
 *
 * 为什么是"跳过去"而不是"显示出来"：身份码由各平台**动态生成**（还会刷新），
 * 第三方拿不到也不该拿；各家的页面就是唯一权威来源。
 *
 * ⚠️ 隐私：本页**不读取、不显示、不保存**身份码内容；识别管线另有"身份码/出库码页面拒采"保护
 * （见 RecognitionPipeline 的敏感页面拦截），确保它不会被自动截图入库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityCodeScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 安装状态在进入页面时读一次（需要 AndroidManifest 的 <queries> 才能查到）
    val installed = remember {
        mapOf(
            IdentityCodeLauncher.TAOBAO to IdentityCodeLauncher.isInstalled(context, IdentityCodeLauncher.TAOBAO),
            IdentityCodeLauncher.CAINIAO to IdentityCodeLauncher.isInstalled(context, IdentityCodeLauncher.CAINIAO),
            IdentityCodeLauncher.PINDUODUO to IdentityCodeLauncher.isInstalled(context, IdentityCodeLauncher.PINDUODUO)
        )
    }

    fun open(block: (android.content.Context) -> IdentityCodeLauncher.Result) {
        scope.launch {
            val result = withContext(Dispatchers.Main) { block(context) }
            // 文案统一在 IdentityCodeLauncher.resultMessage 里维护
            IdentityCodeLauncher.resultMessage(result)?.let { snackbar.showSnackbar(it) }
        }
    }

    top.yukonga.miuix.kmp.basic.Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = "身份码",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { top.yukonga.miuix.kmp.basic.SnackbarHost(state = snackbar) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(UiSpacing.page),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.group)
        ) {
            Text(
                "选择平台，打开它的身份码页面。身份码由平台动态生成，本应用不读取也不保存。",
                style = MaterialTheme.typography.bodyMedium
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    IdentityEntry(
                        icon = R.drawable.ic_brand_taobao,
                        title = "淘宝身份码",
                        subtitle = "打开淘宝中的身份码",
                        installed = installed[IdentityCodeLauncher.TAOBAO] == true,
                        onClick = { open { IdentityCodeLauncher.openTaobao(it) } }
                    )
                    HorizontalDivider()
                    IdentityEntry(
                        icon = R.drawable.ic_brand_cainiao,
                        title = "菜鸟出库码",
                        subtitle = "身份码（驿站出库用）",
                        installed = installed[IdentityCodeLauncher.CAINIAO] == true,
                        onClick = { open { IdentityCodeLauncher.openCainiao(it) } }
                    )
                    HorizontalDivider()
                    IdentityEntry(
                        icon = R.drawable.ic_brand_pinduoduo,
                        title = "拼多多身份码",
                        subtitle = "快递包裹页",
                        installed = installed[IdentityCodeLauncher.PINDUODUO] == true,
                        onClick = { open { IdentityCodeLauncher.openPinduoduo(it) } }
                    )
                }
            }

            Spacer(Modifier.height(UiSpacing.item))
            IconText(
                R.drawable.ic_lock,
                "身份码等于取件授权，请勿截图外传。",
                iconSize = 15.dp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun IdentityEntry(
    @androidx.annotation.DrawableRes icon: Int,
    title: String,
    subtitle: String,
    installed: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .let { if (installed) it.clickable(role = Role.Button, onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandBadge(res = icon, contentDescription = null, boxSize = 36.dp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (installed) subtitle else "未安装",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (installed) Text("打开", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
