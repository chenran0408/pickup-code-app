package com.pickupcode.app.service

import android.content.Context
import com.pickupcode.app.data.CodeRepository
import com.pickupcode.app.extractor.CodeExtractor
import com.pickupcode.app.extractor.CodeValidator
import com.pickupcode.app.notification.CodeNotificationManager
import com.pickupcode.app.preferences.AppPreferences

/** 归档记录时同步撤销该码的通知和提醒。旧通知只能操作仍活跃的原记录。 */
object CodeCompletion {
    enum class EditCodeResult { SAVED, INVALID, DUPLICATE, MISSING }

    suspend fun changeCode(context: Context, repo: CodeRepository, historyId: Long, input: String): EditCodeResult {
        val record = repo.getByIdSuspend(historyId) ?: return EditCodeResult.MISSING
        val code = input.trim()
        val type = CodeExtractor.CodeType.entries.firstOrNull { it.name == record.type }
            ?: return EditCodeResult.INVALID
        val valid = if (type == CodeExtractor.CodeType.coupon) CodeValidator.isValidCouponPayload(code)
            else CodeValidator.isValidManualCode(code, record.type)
        if (!valid) return EditCodeResult.INVALID
        if (code == record.code) return EditCodeResult.SAVED
        val collision = repo.findMergeTarget(record.copy(code = code))
        if (collision != null && collision.id != historyId) return EditCodeResult.DUPLICATE
        repo.updateCode(historyId, code)
        CodeNotificationManager.dismissRecord(context, record)
        if (record.isActive) {
            if (record.expiryTime > System.currentTimeMillis() && AppPreferences.isExpiryRemindEnabled(context)) {
                CodeNotificationManager.scheduleExpiryReminder(context, code, type, record.source, record.expiryTime, historyId)
            }
            CodeNotificationManager.show(context, code, type, record.source, historyId)
        }
        return EditCodeResult.SAVED
    }

    suspend fun markDone(context: Context, repo: CodeRepository, historyId: Long): Long? {
        val record = repo.getByIdSuspend(historyId)?.takeIf { it.isActive } ?: return null
        val doneAt = System.currentTimeMillis()
        repo.markDone(historyId, doneAt)
        val type = CodeExtractor.CodeType.entries.firstOrNull { it.name == record.type }
        if (type != null) {
            CodeNotificationManager.dismissRecord(context, record)
        }
        return doneAt
    }

    suspend fun restore(context: Context, repo: CodeRepository, historyId: Long) {
        val record = repo.getByIdSuspend(historyId)?.takeIf { !it.isActive } ?: return
        repo.restore(historyId)
        if (record.expiryTime > System.currentTimeMillis() &&
            AppPreferences.isExpiryRemindEnabled(context)
        ) {
            val type = CodeExtractor.CodeType.entries.firstOrNull { it.name == record.type }
            if (type != null) {
                CodeNotificationManager.scheduleExpiryReminder(
                    context, record.code, type, record.source, record.expiryTime, historyId
                )
            }
        }
    }
}
