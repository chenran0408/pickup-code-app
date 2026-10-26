package com.pickupcode.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import com.pickupcode.app.ui.miuix.DropdownMenu
import com.pickupcode.app.ui.miuix.DropdownMenuItem
import com.pickupcode.app.ui.miuix.FilledTonalButton
import com.pickupcode.app.ui.miuix.Icon
import com.pickupcode.app.ui.miuix.IconButton
import com.pickupcode.app.ui.miuix.Text
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pickupcode.app.R
import com.pickupcode.app.data.CodeHistory
import com.pickupcode.app.ui.components.BrandBadge
import com.pickupcode.app.ui.components.BrandLogo
import com.pickupcode.app.ui.components.IconText
import com.pickupcode.app.ui.theme.UiSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CodeHistoryCard(item: CodeHistory, onClick: () -> Unit, onDone: () -> Unit,
    onDelete: () -> Unit, onCopy: () -> Unit, now: Long, modifier: Modifier = Modifier) {
    var menu by remember(item.id) { mutableStateOf(false) }
    val formattedTime = remember(item.timestamp) {
        Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault()).format(CARD_TIME_FORMATTER)
    }
    val logo = remember(item.source, item.shareSourceName, item.shareSourcePkg) {
        BrandLogo.logoRes(item.source, item.shareSourceName, item.shareSourcePkg)
    }
    MiuixCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = UiSpacing.page, vertical = UiSpacing.compact),
        cornerRadius = 20.dp,
        colors = MiuixCardDefaults.defaultColors(
            color = if (item.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stackActions = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f
            val doneButton: @Composable () -> Unit = {
                FilledTonalButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp), shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (item.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (item.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text(if (item.isActive) { if (item.type == "coupon") "标记已使用" else "标记已取" } else { if (item.type == "coupon") "恢复未使用" else "恢复未取" })
                }
            }
            Column(Modifier.padding(start = UiSpacing.page, end = UiSpacing.item, top = UiSpacing.item, bottom = UiSpacing.item + 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.code, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = if (item.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace, maxLines = 2,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .clickable(onClickLabel = "复制码值", onClick = onCopy).wrapContentHeight(Alignment.CenterVertically))
                    if (!stackActions) doneButton()
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                            Icon(MiuixIcons.More, "更多记录操作")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("查看详情") }, onClick = { menu = false; onClick() })
                            DropdownMenuItem(text = { Text("复制码值") }, onClick = { menu = false; onCopy() })
                            DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onDelete() })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (logo != null) BrandBadge(res = logo, contentDescription = null, boxSize = 20.dp)
                    Text(item.source.ifBlank { when (item.type) { "coupon" -> "券码"; "pickup_food" -> "取餐"; else -> "取件" } },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (item.pickupAddress.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    IconText(R.drawable.ic_map_pin, item.pickupAddress, iconSize = 14.dp,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
                if (item.cabinetNumber.isNotBlank()) {
                    Text("柜号 ${item.cabinetNumber}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.pickupcode.app.ui.components.RecordStatusBadge(item, now)
                    Text(formattedTime, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 堆叠布局时操作按钮靠左，给右下角悬浮添加按钮留出触控空间。
                if (stackActions) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { doneButton() }
            }
        }
    }
}

private val CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")
