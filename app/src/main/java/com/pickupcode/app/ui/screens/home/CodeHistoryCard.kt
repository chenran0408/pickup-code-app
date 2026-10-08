package com.pickupcode.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import com.pickupcode.app.ui.miuix.*
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
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (item.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow)) {
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
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
                com.pickupcode.app.ui.components.RecordStatusBadge(item, now)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.code, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = if (item.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .clickable(onClickLabel = "复制码值", onClick = onCopy).wrapContentHeight(Alignment.CenterVertically))
                    if (!stackActions) doneButton()
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.MoreVert, "更多记录操作")
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
                    Text("${item.source.ifBlank { when (item.type) { "coupon" -> "券码"; "pickup_food" -> "取餐"; else -> "取件" } }} · $formattedTime",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.pickupAddress.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    IconText(R.drawable.ic_map_pin, item.pickupAddress, iconSize = 14.dp,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                if (stackActions) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { doneButton() }
            }
        }
    }
}

private val CARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")
