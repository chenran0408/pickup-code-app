package com.pickupcode.app.ui.screens

import com.pickupcode.app.service.NotificationAppSelection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NotificationAppListTest {
    private val apps = listOf(NotificationAppEntry("com.example.parcel", "快递助手"),
        NotificationAppEntry("sms", "短信", system = true),
        NotificationAppEntry("service", "系统服务", system = true, launcher = false),
        NotificationAppEntry("self", "码上闪记"))
    private fun project(selected: Set<String> = emptySet(), query: String = "", only: Boolean = false,
        system: Boolean = false, sms: String? = "sms") = NotificationAppList.project(apps, selected, sms, "self", query, only, system)
    @Test fun `search supports names packages and selected filter together`() {
        assertEquals(listOf("com.example.parcel"), project(query = "快递").map { it.packageName })
        assertEquals(listOf("com.example.parcel"), project(query = " EXAMPLE ").map { it.packageName })
        assertTrue(project(selected = setOf("sms"), query = "快递", only = true).isEmpty())
        assertTrue(project(query = "码上").isEmpty())
    }
    @Test fun `system services can be expanded and selected unavailable apps remain removable`() {
        assertFalse(project().any { it.packageName == "service" })
        assertTrue(project(system = true).any { it.packageName == "service" })
        assertTrue(project(selected = setOf("service")).any { it.packageName == "service" })
        val missing = project(selected = setOf("missing"), only = true).single()
        assertFalse(missing.available)
        assertEquals("missing", missing.packageName)
        assertEquals(listOf("sms"), project(selected = setOf(NotificationAppSelection.DEFAULT_SMS), only = true).map { it.packageName })
        assertEquals(NotificationAppSelection.DEFAULT_SMS, project(selected = setOf(NotificationAppSelection.DEFAULT_SMS), only = true, sms = null).single().packageName)
    }
    @Test fun `selecting does not reorder installed apps and large list search retains exact entries`() {
        assertEquals(project().map { it.packageName }, project(selected = setOf("sms")).map { it.packageName })
        val large = (1..1000).map { NotificationAppEntry("app.$it", "测试应用$it") }
        val found = NotificationAppList.project(large, setOf("app.952"), null, "self", "APP.952", true, false)
        assertEquals(listOf("app.952"), found.map { it.packageName })
    }
}
