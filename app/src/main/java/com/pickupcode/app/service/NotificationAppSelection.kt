package com.pickupcode.app.service

/** 默认短信选择随系统默认应用变化；空集合明确表示全部关闭，不能回退旧开关。 */
object NotificationAppSelection {
    const val DEFAULT_SMS = "@default_sms"
    fun legacy(sms: Boolean, wechat: Boolean, taobao: Boolean, pinduoduo: Boolean, jd: Boolean): Set<String> = buildSet {
        if (sms) add(DEFAULT_SMS)
        if (wechat) add("com.tencent.mm")
        if (taobao) add("com.taobao.taobao")
        if (pinduoduo) add("com.xunmeng.pinduoduo")
        if (jd) add("com.jingdong.app.mall")
    }
    fun resolve(stored: Set<String>?, sms: Boolean, wechat: Boolean, taobao: Boolean,
        pinduoduo: Boolean, jd: Boolean): Set<String> = stored ?: legacy(sms, wechat, taobao, pinduoduo, jd)
    fun accepts(actual: String, defaultSms: String?, summary: Boolean, ownPackage: String,
        selected: Set<String>): Boolean = !summary && actual != ownPackage &&
        (actual in selected || (DEFAULT_SMS in selected && !defaultSms.isNullOrBlank() && actual == defaultSms))
    fun key(packageName: String, defaultSms: String?): String =
        if (!defaultSms.isNullOrBlank() && packageName == defaultSms) DEFAULT_SMS else packageName
    fun contains(selected: Set<String>, packageName: String, defaultSms: String?): Boolean =
        packageName in selected || key(packageName, defaultSms) in selected
}
