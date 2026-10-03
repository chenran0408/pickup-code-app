package com.pickupcode.app.service

/** 不读取历史消息；有 MessagingStyle 时只处理最新一条收到的消息。 */
object SmsNotificationContent {
    data class Message(val text: String, val incoming: Boolean)

    fun acceptPackage(actual: String, defaultSms: String?, summary: Boolean,
        smsEnabled: Boolean = true, wechatEnabled: Boolean = false): Boolean =
        !summary && ((smsEnabled && !defaultSms.isNullOrBlank() && actual == defaultSms) ||
            (wechatEnabled && actual == WECHAT_PACKAGE))

    const val WECHAT_PACKAGE = "com.tencent.mm"

    /** 普通聊天、账号数字和运单通知不凭裸数字生成取件记录。 */
    private val pickupContext = Regex("取件|取货|取餐|取单|提货|包裹|驿站|代收点|快递柜|开箱")
    fun hasPickupContext(body: String): Boolean = pickupContext
        .containsMatchIn(body)

    fun body(text: String?, expanded: String?, lines: List<String>, messages: List<Message>): String {
        if (messages.isNotEmpty()) {
            val latest = messages.last()
            return if (latest.incoming) latest.text.take(20000) else ""
        }
        return (expanded?.takeIf { it.isNotBlank() } ?: text?.takeIf { it.isNotBlank() }
            ?: lines.lastOrNull().orEmpty()).take(20000)
    }
}
