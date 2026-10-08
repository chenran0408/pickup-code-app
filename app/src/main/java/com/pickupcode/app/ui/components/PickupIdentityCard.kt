package com.pickupcode.app.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import com.pickupcode.app.ui.miuix.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pickupcode.app.R
import com.pickupcode.app.util.IdentityCodeLauncher

/** 三个平台直接打开各自入口，取件时不用再经过应用内的选择页。 */
@Composable
fun PickupIdentityCard(compact: Boolean = false) {
    val context = LocalContext.current
    // 紧凑入口使用 surface 背景，文字和图标必须使用与该背景配套的前景色。
    val foreground = if (compact) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    val entries = androidx.compose.runtime.remember { listOf(
        IdentityEntry("淘宝码", R.drawable.ic_brand_taobao, IdentityCodeLauncher::openTaobao),
        IdentityEntry("菜鸟码", R.drawable.ic_brand_cainiao, IdentityCodeLauncher::openCainiao),
        IdentityEntry("拼多多码", R.drawable.ic_brand_pinduoduo, IdentityCodeLauncher::openPinduoduo)
    ) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 8.dp)) {
        entries.forEachIndexed { index, entry ->
            if (compact && index > 0) VerticalDivider(Modifier.height(32.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Card(onClick = {
                IdentityCodeLauncher.resultMessage(entry.open(context))?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = if (compact) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().heightIn(min = if (compact) { if (largeText) 72.dp else 56.dp } else 88.dp).padding(horizontal = 8.dp, vertical = if (compact) 6.dp else 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Image(painterResource(entry.icon), null,
                        colorFilter = ColorFilter.tint(foreground),
                        modifier = Modifier.size(if (compact) { if (largeText) 26.dp else 20.dp } else 32.dp))
                    Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
                    Text(entry.label, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall, maxLines = if (largeText) 2 else 1, fontWeight = FontWeight.SemiBold,
                        color = foreground)
                }
            }
        }
    }
}

private data class IdentityEntry(val label: String, val icon: Int, val open: (Context) -> IdentityCodeLauncher.Result)
