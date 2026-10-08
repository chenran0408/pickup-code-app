package com.pickupcode.app.ui.screens

import com.pickupcode.app.service.NotificationAppSelection

data class NotificationAppEntry(val packageName: String, val label: String,
    val system: Boolean = false, val launcher: Boolean = true, val available: Boolean = true)

object NotificationAppList {
    fun project(installed: List<NotificationAppEntry>, selected: Set<String>, defaultSms: String?,
        ownPackage: String, query: String, selectedOnly: Boolean, showSystem: Boolean): List<NotificationAppEntry> {
        val visible = installed.filter { it.packageName != ownPackage }.associateBy { it.packageName }.toMutableMap()
        val missing = selected.filter { it != NotificationAppSelection.DEFAULT_SMS && it != ownPackage }.toMutableSet()
        if (NotificationAppSelection.DEFAULT_SMS in selected) missing.add(defaultSms ?: NotificationAppSelection.DEFAULT_SMS)
        for (pkg in missing) if (pkg !in visible) visible[pkg] = NotificationAppEntry(pkg,
            if (pkg == NotificationAppSelection.DEFAULT_SMS) "默认短信应用（未设置）" else pkg, available = false)
        val search = query.trim()
        return visible.values.asSequence().filter { app ->
            val checked = NotificationAppSelection.contains(selected, app.packageName, defaultSms)
            (!selectedOnly || checked) && (showSystem || !app.system || app.launcher || checked || app.packageName == defaultSms) &&
                (search.isEmpty() || app.label.contains(search, true) || app.packageName.contains(search, true))
        }.sortedWith(compareBy<NotificationAppEntry> { !it.available }.thenBy { it.label.lowercase() }.thenBy { it.packageName }).toList()
    }
}
