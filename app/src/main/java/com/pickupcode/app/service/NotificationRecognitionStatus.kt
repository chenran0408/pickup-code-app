package com.pickupcode.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** 只记录运行状态与计数，不保存通知正文、联系人或码值。 */
object NotificationRecognitionStatus {
    data class Event(val at: Long, val readable: Boolean, val saved: Int = 0)
    enum class Connection { IDLE, OFF, NO_ACCESS, CONNECTING, CONNECTED, FAILED }
    data class State(val connected: Boolean = false, val events: Map<String, Event> = emptyMap(),
        val connection: Connection = Connection.IDLE)
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable
    fun connected(value: Boolean) {
        mutable.update { it.copy(connected = value, connection = when {
            value -> Connection.CONNECTED
            it.connection == Connection.CONNECTED -> Connection.IDLE
            else -> it.connection
        }) }
    }
    fun connection(value: Connection) { mutable.update { it.copy(connection = value) } }
    fun description(selectedCount: Int, granted: Boolean, state: State): String = when {
        selectedCount == 0 -> "未选择应用，通知识别已关闭"
        !granted -> "请开启通知访问权限"
        state.connected -> "通知识别已连接"
        state.connection == Connection.CONNECTING -> "正在连接通知识别…"
        state.connection == Connection.FAILED -> "系统未连接通知识别，请允许应用自启动并取消后台限制后重试"
        else -> "通知识别未连接"
    }
    fun received(packageName: String, readable: Boolean, saved: Int = 0) {
        val event = Event(System.currentTimeMillis(), readable, saved)
        mutable.update { it.copy(events = it.events + (packageName to event)) }
    }
}
