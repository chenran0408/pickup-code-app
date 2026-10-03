package com.pickupcode.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 只记录运行状态与计数，不保存通知正文、联系人或码值。 */
object NotificationRecognitionStatus {
    data class Event(val at: Long, val readable: Boolean, val saved: Int = 0)
    data class State(val connected: Boolean = false, val sms: Event? = null, val wechat: Event? = null)
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable
    fun connected(value: Boolean) { mutable.value = mutable.value.copy(connected = value) }
    fun received(wechat: Boolean, readable: Boolean, saved: Int = 0) {
        val event = Event(System.currentTimeMillis(), readable, saved)
        mutable.value = if (wechat) mutable.value.copy(wechat = event) else mutable.value.copy(sms = event)
    }
}
