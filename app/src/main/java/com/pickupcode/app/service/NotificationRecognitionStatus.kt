package com.pickupcode.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** 只记录运行状态与计数，不保存通知正文、联系人或码值。 */
object NotificationRecognitionStatus {
    data class Event(val at: Long, val readable: Boolean, val saved: Int = 0)
    data class State(val connected: Boolean = false, val events: Map<String, Event> = emptyMap())
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable
    fun connected(value: Boolean) { mutable.update { it.copy(connected = value) } }
    fun received(packageName: String, readable: Boolean, saved: Int = 0) {
        val event = Event(System.currentTimeMillis(), readable, saved)
        mutable.update { it.copy(events = it.events + (packageName to event)) }
    }
}
