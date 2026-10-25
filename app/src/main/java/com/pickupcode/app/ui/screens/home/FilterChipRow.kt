package com.pickupcode.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.pickupcode.app.ui.theme.TypeCoupon
import com.pickupcode.app.ui.theme.TypeFood
import com.pickupcode.app.ui.theme.TypeParcel

/** Miuix button-based type filters keep selected semantics without a Material Chip facade. */
@Composable
fun FilterChipRow(currentFilter: String, onFilterChange: (String) -> Unit) {
    val filters = listOf("all" to "全部", "food" to "取餐", "parcel" to "取件", "coupon" to "券码")
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        filters.forEach { (key, label) ->
            val selected = currentFilter == key
            val tint = when (key) {
                "food" -> TypeFood
                "parcel" -> TypeParcel
                "coupon" -> TypeCoupon
                else -> Color.Unspecified
            }
            Button(
                onClick = { onFilterChange(key) },
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    this.selected = selected
                },
                minHeight = 48.dp,
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonColors(
                    color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryVariant,
                    disabledColor = MiuixTheme.colorScheme.secondaryVariant,
                    contentColor = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    disabledContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                ),
                cornerRadius = 16.dp,
            ) {
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                } else if (tint != Color.Unspecified) {
                    Box(Modifier.size(7.dp).background(tint, CircleShape))
                }
                Spacer(Modifier.size(8.dp))
                Text(label)
            }
        }
    }
}
