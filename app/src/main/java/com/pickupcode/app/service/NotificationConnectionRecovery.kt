package com.pickupcode.app.service

/** 连接成功必须来自系统回调；请求重绑成功返回并不代表已经连接。 */
internal suspend fun recoverNotificationConnection(
    enabled: suspend () -> Boolean,
    connected: () -> Boolean,
    request: suspend () -> Unit,
    awaitConnection: suspend () -> Unit,
): NotificationConnectionResult {
    repeat(3) {
        if (!enabled()) return NotificationConnectionResult.INACTIVE
        if (connected()) return NotificationConnectionResult.CONNECTED
        request()
        awaitConnection()
        if (!enabled()) return NotificationConnectionResult.INACTIVE
        if (connected()) return NotificationConnectionResult.CONNECTED
    }
    return NotificationConnectionResult.FAILED
}

internal enum class NotificationConnectionResult { CONNECTED, INACTIVE, FAILED }
