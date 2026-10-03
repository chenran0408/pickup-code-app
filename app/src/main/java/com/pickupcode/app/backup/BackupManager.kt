package com.pickupcode.app.backup

import android.content.Context
import android.net.Uri
import com.pickupcode.app.data.AppDatabase
import com.pickupcode.app.extractor.CodeExtractor
import com.pickupcode.app.learner.PatternLearner
import com.pickupcode.app.learner.SavedAddressStore
import com.pickupcode.app.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {
    suspend fun export(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val settings = AppPreferences.observe(context).first()
        val addresses = JSONArray(SavedAddressStore.getAll(context).map { a ->
            JSONObject().put("fullName", a.fullName).put("keywords", JSONArray(a.keywords)).put("enabled", a.enabled)
        })
        val custom = JSONArray(PatternLearner.getLearnedPatterns(context).map { rule ->
            JSONObject().put("regex", rule.regex).put("type", rule.type).put("label", rule.label).put("enabled", rule.enabled)
        })
        val overrides = PatternLearner.getBuiltinOverrides(context)
        val rules = JSONObject().put("custom", custom).put("disabled", JSONArray(overrides.disabled.toList()))
            .put("edits", JSONObject(overrides.regexEdits))
        val content = BackupCodec.encode(BackupCodec.Payload(AppDatabase.getInstance(context).repository.getAll(),
            AppPreferences.localBackup(settings), addresses, rules))
        require(content.toByteArray().size <= BackupCodec.MAX_BYTES) { "备份超过8MB，请先清理不需要的记录" }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: error("无法写入所选文件")
    }

    suspend fun read(context: Context, uri: Uri): BackupCodec.Payload = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytesBounded(BackupCodec.MAX_BYTES)
            bytes.toString(Charsets.UTF_8)
        } ?: error("无法读取所选文件")
        BackupCodec.decode(content)
    }

    /** 追加记录及新地址，已存在的数据优先；设置恢复必须由用户在预览中主动选择。 */
    suspend fun restore(context: Context, payload: BackupCodec.Payload, restoreSettings: Boolean): Int = withContext(Dispatchers.IO) {
        val repo = AppDatabase.getInstance(context).repository
        val oldAddresses = SavedAddressStore.getAll(context)
        val incomingNames = (0 until payload.addresses.length()).map { payload.addresses.getJSONObject(it).getString("fullName") }
        require((oldAddresses.map { it.fullName } + incomingNames).distinct().size <= SavedAddressStore.MAX_ENTRIES) { "恢复后常用地址超过50条" }
        if (restoreSettings) for (i in 0 until payload.rules.getJSONArray("custom").length()) {
            require(PatternLearner.validateRegex(payload.rules.getJSONArray("custom").getJSONObject(i).getString("regex")).ok) { "备份包含不安全的正则" }
        }
        val count = repo.importRecords(payload.records)
        val existing = SavedAddressStore.getAll(context).map { it.fullName }.toMutableSet()
        for (i in 0 until payload.addresses.length()) {
            val a = payload.addresses.getJSONObject(i)
            if (existing.add(a.getString("fullName"))) SavedAddressStore.upsert(context, SavedAddressStore.SavedAddress(
                fullName = a.getString("fullName"), keywords = a.getJSONArray("keywords").let { array ->
                    (0 until array.length()).map { array.getString(it) } }, enabled = a.getBoolean("enabled")))
        }
        if (restoreSettings) {
            AppPreferences.restoreLocalBackup(context, payload.localSettings)
            val custom = payload.rules.getJSONArray("custom")
            val existingRules = PatternLearner.getLearnedPatterns(context).map { it.regex }.toMutableSet()
            for (i in 0 until custom.length()) {
                val rule = custom.getJSONObject(i)
                if (existingRules.add(rule.getString("regex"))) {
                    val added = PatternLearner.addUserRule(context, rule.getString("regex"), rule.getString("type"), rule.getString("label"))
                    if (added.isSuccess && !rule.getBoolean("enabled")) PatternLearner.setRuleEnabled(context, rule.getString("regex"), false)
                }
            }
            val known = CodeExtractor.builtinRuleInfos(context).associateBy { it.id }
            val disabled = payload.rules.getJSONArray("disabled")
            for (i in 0 until disabled.length()) disabled.getString(i).takeIf { it in known }?.let { PatternLearner.setBuiltinEnabled(context, it, false) }
            val edits = payload.rules.getJSONObject("edits")
            edits.keys().forEach { id -> if (known[id]?.editable == true) PatternLearner.setBuiltinRegex(context, id, edits.getString(id)) }
        }
        // 恢复的活跃记录重排未来到期提醒；已取及过期记录不重新打扰。
        if (AppPreferences.isExpiryRemindEnabled(context)) repo.getAll().filter { it.isActive && it.expiryTime > System.currentTimeMillis() }.forEach {
            com.pickupcode.app.notification.CodeNotificationManager.scheduleExpiryReminder(context, it.code,
                CodeExtractor.CodeType.valueOf(it.type), it.source, it.expiryTime, historyId = it.id)
        }
        count
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer); if (n < 0) break
            require(output.size() + n <= limit) { "备份超过8MB" }; output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }
}
