package com.pickupcode.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pickupcode.app.data.CodeHistory

@Composable
fun RecordStatusBadge(item: CodeHistory, now: Long = System.currentTimeMillis()) {
    val expired = item.isActive && item.expiryTime in 1..now
    val status = when {
        !item.isActive -> if (item.type == "coupon") "已使用" else "已取"
        expired -> if (item.type == "coupon") "未使用 · 已过期" else "未取 · 已过期"
        else -> if (item.type == "coupon") "未使用" else "未取"
    }
    Surface(shape = MaterialTheme.shapes.small,
        color = when { !item.isActive -> MaterialTheme.colorScheme.surfaceVariant
            expired -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.primaryContainer },
        contentColor = when { !item.isActive -> MaterialTheme.colorScheme.onSurfaceVariant
            expired -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer }) {
        Text(status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}
