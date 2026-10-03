package com.pickupcode.app.notification

/** 保留升级前的请求码，取消或替换提醒时可找到系统内已存在的旧 PendingIntent。 */
internal object ReminderRequestCodes {
    fun current(type: String, code: String, kind: String): Int =
        ("$type:$code".hashCode() and 0x1fffffff) or segment(kind)

    fun cancellationCandidates(type: String, code: String, kind: String): Set<Int> = setOf(
        current(type, code, kind),
        ("$type:$code".hashCode() and 0x3fffffff) or segment(kind)
    )

    private fun segment(kind: String): Int = if (kind == "expiry") 0x40000000 else 0x20000000
}
