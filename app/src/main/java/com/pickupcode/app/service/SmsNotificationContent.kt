package com.pickupcode.app.service

/** 不读取历史消息；有 MessagingStyle 时只处理最新一条收到的消息。 */
object SmsNotificationContent {
    data class Message(val text: String, val incoming: Boolean)

    enum class Source(val label: String, val packageName: String, val tag: String) {
        SMS("短信", "", "sms_notification"),
        WECHAT("微信", "com.tencent.mm", "wechat_notification"),
        TAOBAO("淘宝", "com.taobao.taobao", "taobao_notification"),
        PINDUODUO("拼多多", "com.xunmeng.pinduoduo", "pinduoduo_notification"),
        JD("京东", "com.jingdong.app.mall", "jd_notification");
        val shopping: Boolean get() = this == TAOBAO || this == PINDUODUO || this == JD
    }

    const val WECHAT_PACKAGE = "com.tencent.mm"

    fun sourceFor(actual: String, defaultSms: String?, summary: Boolean): Source? {
        if (summary) return null
        return Source.entries.firstOrNull { it.packageName.isNotEmpty() && it.packageName == actual }
            ?: Source.SMS.takeIf { !defaultSms.isNullOrBlank() && actual == defaultSms }
    }

    fun acceptPackage(actual: String, defaultSms: String?, summary: Boolean,
        smsEnabled: Boolean = true, wechatEnabled: Boolean = false,
        taobaoEnabled: Boolean = false, pinduoduoEnabled: Boolean = false,
        jdEnabled: Boolean = false): Boolean = when (sourceFor(actual, defaultSms, summary)) {
        Source.SMS -> smsEnabled
        Source.WECHAT -> wechatEnabled
        Source.TAOBAO -> taobaoEnabled
        Source.PINDUODUO -> pinduoduoEnabled
        Source.JD -> jdEnabled
        null -> false
    }

    /** 购物通知可能把“取件码”放在标题、码值放在正文；不从只有标题的隐藏通知识别。 */
    fun recognitionText(source: Source, title: String?, body: String): String {
        if (body.isBlank()) return ""
        return if (source.shopping && !title.isNullOrBlank())
            "${title.take(200)}\n$body".take(20000) else body.take(20000)
    }

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
