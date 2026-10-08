package com.pickupcode.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pickupcode.app.App
import com.pickupcode.app.data.AppDatabase
import com.pickupcode.app.extractor.AddressExtractor
import com.pickupcode.app.extractor.CodeExtractor
import com.pickupcode.app.ocr.OCREngine
import com.pickupcode.app.preferences.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

/** 可选的短信、微信与购物应用入口：仅所选应用的新通知，本地识别，不调用在线 AI。 */
class SmsNotificationListener : NotificationListenerService() {
    private val mutex = Mutex()
    private val recent = LinkedHashMap<String, String>()

    override fun onListenerConnected() {
        NotificationRecognitionStatus.connected(true)
        Log.d(TAG, "取件码通知监听已连接，默认短信应用=${Telephony.Sms.getDefaultSmsPackage(this).orEmpty()}")
    }

    override fun onListenerDisconnected() {
        NotificationRecognitionStatus.connected(false)
        if (hasAccess(this)) requestRebind(ComponentName(this, SmsNotificationListener::class.java))
    }

    override fun onDestroy() {
        NotificationRecognitionStatus.connected(false)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
        val source = SmsNotificationContent.sourceFor(sbn.packageName, defaultSms,
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) ?: return
        App.appScope.launch {
            try {
                mutex.withLock {
                    val settings = AppPreferences.observe(applicationContext).first()
                    if (!SmsNotificationContent.acceptPackage(sbn.packageName, defaultSms,
                            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                            settings.enableSmsNotifications, settings.enableWechatNotifications,
                            settings.enableTaobaoNotifications, settings.enablePinduoduoNotifications,
                            settings.enableJdNotifications)) return@withLock
                    val sourceName = "${source.label}通知"
                    val sourceTag = source.tag
                    val extras = notification.extras ?: return@withLock
                    val messages = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                        ?.messages.orEmpty().map {
                        SmsNotificationContent.Message(it.text?.toString().orEmpty(), it.person != null)
                    }
                    val visibleBody = SmsNotificationContent.body(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty(), messages)
                    val body = SmsNotificationContent.recognitionText(source,
                        extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(), visibleBody)
                    NotificationRecognitionStatus.received(source, body.isNotBlank())
                    if (body.isBlank()) { Log.d(TAG, "$sourceName 无可读的收到消息正文"); return@withLock }
                    if (com.pickupcode.app.util.SensitivePageGuard.isIdentityCodePage(body)) return@withLock
                    val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    if (recent[sbn.key] == digest) return@withLock
                    if (CodeExtractor.isFinancialNoise(body) || !SmsNotificationContent.hasPickupContext(body)) return@withLock
                    val completed = withTimeoutOrNull(15000) {
                        val lines = body.lines().filter { it.isNotBlank() }.map { OCREngine.TextLine(it, null, 1f) }
                        val codes = CodeExtractor.extract(lines, context = applicationContext, source = sourceTag)
                            .filter { it.confidence >= settings.confidenceThreshold &&
                                RecognitionPipeline.isTypeEnabled(it.type, settings) && it.type != CodeExtractor.CodeType.coupon }
                        val repo = AppDatabase.getInstance(applicationContext).repository
                        val saved = if (codes.isEmpty()) emptyList() else RecognitionPipeline.finalize(
                            context = applicationContext, allResults = codes.map { it.code to it.type },
                            codeSources = codes.associate { it.code to it.source }, lines = lines, allText = body,
                            fullAddress = AddressExtractor.extractAddressFromStores(applicationContext, lines, body),
                            rawSnippet = body, shareSourcePkg = sbn.packageName, shareSourceName = sourceName,
                            timestamp = sbn.postTime, repo = repo)
                        for (record in saved.filterNot { it.existed }) RecognitionPipeline.notifySaved(
                            applicationContext, { repo.countDuplicateGroups() }, record.code, record.type,
                            record.source, record.id, false)
                        NotificationRecognitionStatus.received(source, true, saved.size)
                        Log.d(TAG, "$sourceName 识别完成，候选=${codes.size}，保存=${saved.size}")
                        true
                    }
                    if (completed == true) {
                        recent[sbn.key] = digest
                        while (recent.size > 100) recent.remove(recent.keys.first())
                    } else Log.w(TAG, "$sourceName 识别超时")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "取件码通知处理失败: ${e.javaClass.simpleName}") }
        }
    }

    companion object {
        private const val TAG = "SmsNotificationListener"
        fun hasAccess(context: Context): Boolean {
            val component = ComponentName(context, SmsNotificationListener::class.java)
            return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
