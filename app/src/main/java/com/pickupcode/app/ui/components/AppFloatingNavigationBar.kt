package com.pickupcode.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.pickupcode.app.ui.navigation.RootDestination
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Settings

/** Miuix 原生悬浮底栏；选择状态直接由 MainActivity 的页面路由提供。 */
@Composable
fun AppFloatingNavigationBar(
    selectedDestination: RootDestination,
    onDestinationSelected: (RootDestination) -> Unit,
) {
    FloatingNavigationBar {
        RootDestination.entries.forEach { destination ->
            val label = when (destination) {
                RootDestination.HOME -> "首页"
                RootDestination.IDENTITY_CODE -> "身份码"
                RootDestination.SETTINGS -> "设置"
            }
            val icon = when (destination) {
                RootDestination.HOME -> MiuixIcons.Home
                RootDestination.IDENTITY_CODE -> MiuixIcons.Scan
                RootDestination.SETTINGS -> MiuixIcons.Settings
            }
            val isSelected = destination == selectedDestination
            FloatingNavigationBarItem(
                selected = isSelected,
                onClick = { onDestinationSelected(destination) },
                icon = icon,
                label = label,
                modifier = Modifier.semantics {
                    contentDescription = label
                    role = Role.Tab
                    selected = isSelected
                },
            )
        }
    }
}
