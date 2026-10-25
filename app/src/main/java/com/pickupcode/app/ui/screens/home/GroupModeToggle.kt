package com.pickupcode.app.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.pickupcode.app.R

/** Time/address grouping selector implemented with native Miuix buttons. */
@Composable
fun GroupModeToggle(mode: String, onModeChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf("time" to ("按时间" to R.drawable.ic_timer), "address" to ("按地址" to R.drawable.ic_map_pin))
            .forEach { (key, data) ->
                val (label, iconRes) = data
                val selected = mode == key
                Button(
                    onClick = { onModeChange(key) },
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
                    } else {
                        Image(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(label)
                }
            }
    }
}
