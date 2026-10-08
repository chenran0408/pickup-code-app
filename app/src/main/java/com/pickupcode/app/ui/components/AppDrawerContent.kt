package com.pickupcode.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppDrawerContent(onNavigate: (String) -> Unit) {
    ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Text("码上闪记", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            Text("识别功能", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp))
            for ((key, label) in listOf("notification_apps" to "通知识别", "recognition" to "识别偏好",
                "input" to "识别方式与权限", "verify" to "AI 与辅助识别", "rules" to "自定义规则", "addresses" to "常用取件地址")) {
                NavigationDrawerItem(label = { Text(label) }, selected = false, onClick = { onNavigate(key) })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("记录管理", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp))
            for ((key, label) in listOf("stats" to "识别统计", "dedup" to "重复记录", "trash" to "回收站")) {
                NavigationDrawerItem(label = { Text(label) }, selected = false, onClick = { onNavigate(key) })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(label = { Text("主题与显示") }, selected = false, onClick = { onNavigate("appearance") })
            NavigationDrawerItem(label = { Text("关于与反馈") }, selected = false, onClick = { onNavigate("about") })
            Spacer(Modifier.height(16.dp))
        }
    }
}
