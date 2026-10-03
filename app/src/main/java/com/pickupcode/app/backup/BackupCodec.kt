package com.pickupcode.app.backup

import com.pickupcode.app.data.CodeHistory
import org.json.JSONArray
import org.json.JSONObject

/** 可移植 JSON；不打包设备路径、截图和 API 密钥。先完整校验，再允许恢复。 */
object BackupCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    data class Payload(val records: List<CodeHistory>, val localSettings: JSONObject,
        val addresses: JSONArray, val rules: JSONObject)

    fun encode(payload: Payload): String = JSONObject().put("format", "pickup-code-backup")
        .put("version", 1).put("createdAt", System.currentTimeMillis())
        .put("records", JSONArray(payload.records.map { record ->
            JSONObject().put("code", record.code).put("type", record.type).put("source", record.source)
                .put("rawTextSnippet", record.rawTextSnippet).put("pickupAddress", record.pickupAddress)
                .put("timestamp", record.timestamp).put("isActive", record.isActive).put("doneAt", record.doneAt)
                .put("archiveKind", record.archiveKind).put("expiryTime", record.expiryTime)
                .put("shareSourcePkg", record.shareSourcePkg).put("shareSourceName", record.shareSourceName)
                .put("cabinetNumber", record.cabinetNumber).put("trackingNumber", record.trackingNumber)
                .put("userEditedFields", record.userEditedFields).put("recognitionOrigin", record.recognitionOrigin)
                .put("addressOrigin", record.addressOrigin).put("suggestedAddress", record.suggestedAddress)
                .put("geoVerified", record.geoVerified).put("geoConfidence", record.geoConfidence.toDouble())
                .put("geoFormattedAddress", record.geoFormattedAddress)
        })).put("localSettings", payload.localSettings).put("addresses", payload.addresses)
        .put("rules", payload.rules).toString(2)

    fun decode(text: String): Payload {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过8MB" }
        val root = JSONObject(text)
        require(root.getString("format") == "pickup-code-backup" && root.getInt("version") == 1) { "不支持的备份格式或版本" }
        val array = root.getJSONArray("records")
        require(array.length() <= 10_000) { "记录过多" }
        val records = (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            fun str(key: String, max: Int = 2000): String {
                val value = obj.optString(key, "")
                require(value.length <= max) { "记录字段过长" }; return value
            }
            val code = str("code", 64); val type = str("type", 32)
            require(code.isNotBlank() && type in setOf("pickup_parcel", "pickup_food", "coupon")) { "记录类型或码值无效" }
            val active = obj.getBoolean("isActive")
            val archive = str("archiveKind", 16)
            require(archive in setOf("", "done", "deleted") && (!active || archive.isEmpty())) { "记录状态无效" }
            val timestamp = obj.getLong("timestamp"); val done = obj.getLong("doneAt"); val expiry = obj.getLong("expiryTime")
            require(timestamp >= 0 && done >= 0 && expiry >= 0) { "记录时间无效" }
            val confidence = obj.optDouble("geoConfidence", 0.0).toFloat()
            require(confidence.isFinite() && confidence in 0f..1f) { "地址置信度无效" }
            CodeHistory(code = code, type = type, source = str("source"), rawTextSnippet = str("rawTextSnippet", 20000),
                pickupAddress = str("pickupAddress"), timestamp = timestamp, isActive = active, doneAt = done,
                archiveKind = archive, expiryTime = expiry, shareSourcePkg = str("shareSourcePkg", 256),
                shareSourceName = str("shareSourceName"), cabinetNumber = str("cabinetNumber"), trackingNumber = str("trackingNumber", 128),
                userEditedFields = obj.optInt("userEditedFields", 0) and 15, recognitionOrigin = str("recognitionOrigin", 32),
                addressOrigin = str("addressOrigin", 32), suggestedAddress = str("suggestedAddress"),
                geoVerified = obj.optBoolean("geoVerified", false), geoConfidence = confidence,
                geoFormattedAddress = str("geoFormattedAddress"))
        }
        val settings = root.getJSONObject("localSettings")
        require(settings.optDouble("confidenceThreshold", 0.5) in 0.0..1.0) { "识别灵敏度无效" }
        require(settings.optString("darkMode", "system") in setOf("system", "light", "dark")) { "主题设置无效" }
        val addresses = root.getJSONArray("addresses")
        require(addresses.length() <= 50) { "常用地址过多" }
        for (i in 0 until addresses.length()) {
            val a = addresses.getJSONObject(i)
            require(a.getString("fullName").length in 1..2000 && a.getJSONArray("keywords").length() <= 50) { "常用地址无效" }
            a.getBoolean("enabled")
            for (j in 0 until a.getJSONArray("keywords").length()) require(a.getJSONArray("keywords").getString(j).length <= 2000)
        }
        val rules = root.getJSONObject("rules")
        val custom = rules.getJSONArray("custom"); require(custom.length() <= 500) { "规则过多" }
        for (i in 0 until custom.length()) {
            val rule = custom.getJSONObject(i)
            require(rule.getString("regex").length in 1..2000 && rule.getString("label").length <= 500)
            Regex(rule.getString("regex"))
            require(rule.getString("type") in setOf("pickup_parcel", "pickup_food", "coupon"))
            rule.getBoolean("enabled")
        }
        val edits = rules.getJSONObject("edits")
        require(edits.length() <= 100)
        edits.keys().forEach { key -> require(edits.getString(key).length in 1..2000); Regex(edits.getString(key)) }
        require(rules.getJSONArray("disabled").length() <= 100)
        return Payload(records, settings, addresses, rules)
    }
}
