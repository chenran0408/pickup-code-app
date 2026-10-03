package com.pickupcode.app.service

/** 不读取历史消息；有 MessagingStyle 时只处理最新一条收到的消息。 */
object SmsNotificationContent {
    data class Message(val text: String, val incoming: Boolean)

    fun acceptPackage(actual: String, defaultSms: String?, summary: Boolean,
        smsEnabled: Boolean = true, wechatEnabled: Boolean = false): Boolean =
        !summary && ((smsEnabled && !defaultSms.isNullOrBlank() && actual == defaultSms) ||
            (wechatEnabled && actual == WECHAT_PACKAGE))

    const val WECHAT_PACKAGE = "com.tencent.mm"

    fun body(text: String?, expanded: String?, lines: List<String>, messages: List<Message>): String {
        if (messages.isNotEmpty()) {
            val latest = messages.last()
            return if (latest.incoming) latest.text.take(20000) else ""
        }
        return (expanded?.takeIf { it.isNotBlank() } ?: text?.takeIf { it.isNotBlank() }
            ?: lines.lastOrNull().orEmpty()).take(20000)
    }
}
