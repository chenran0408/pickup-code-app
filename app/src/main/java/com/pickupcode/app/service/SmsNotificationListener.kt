package com.pickupcode.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

/** 可选的用户所选应用入口：仅所选应用的新通知，本地识别，不调用在线 AI。 */
class SmsNotificationListener : NotificationListenerService() {
    private val mutex = Mutex()
    private val recent = LinkedHashMap<String, String>()

    override fun onListenerConnected() {
        NotificationRecognitionStatus.connected(true)
        Log.d(TAG, "取件码通知监听已连接，默认短信应用=${Telephony.Sms.getDefaultSmsPackage(this).orEmpty()}")
    }

    override fun onListenerDisconnected() {
        NotificationRecognitionStatus.connected(false)
        NotificationListenerConnection.recover(this)
    }

    override fun onDestroy() {
        NotificationRecognitionStatus.connected(false)
        super.onDestroy()
        // 部分系统只销毁服务而不派发断连回调；恢复流程会重新检查授权和来源选择。
        NotificationListenerConnection.recover(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
        val summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (summary || sbn.packageName == packageName) return
        App.appScope.launch {
            try {
                val selected = AppPreferences.observeNotificationApps(applicationContext).first()
                if (!NotificationAppSelection.accepts(sbn.packageName, defaultSms, summary, packageName, selected)) return@launch
                mutex.withLock {
                    val settings = AppPreferences.observe(applicationContext).first()
                    if (!NotificationAppSelection.accepts(sbn.packageName, defaultSms, summary,
                            packageName, settings.notificationApps)) return@withLock
                    val source = SmsNotificationContent.sourceFor(sbn.packageName, defaultSms, false)
                    val sourceName = source?.label?.plus("通知") ?: runCatching {
                        packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
                    }.getOrDefault(sbn.packageName)
                    val sourceTag = source?.tag ?: "app_notification"
                    val extras = notification.extras ?: return@withLock
                    val messages = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                        ?.messages.orEmpty().map {
                        SmsNotificationContent.Message(it.text?.toString().orEmpty(), it.person != null)
                    }
                    val visibleBody = SmsNotificationContent.body(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty(), messages)
                    val body = SmsNotificationContent.recognitionText(
                        source != SmsNotificationContent.Source.SMS && source != SmsNotificationContent.Source.WECHAT,
                        extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(), visibleBody)
                    NotificationRecognitionStatus.received(sbn.packageName, body.isNotBlank())
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
                        NotificationRecognitionStatus.received(sbn.packageName, true, saved.size)
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
        fun openAccess(context: Context) {
            try {
                val component = ComponentName(context, SmsNotificationListener::class.java)
                val intent = if (android.os.Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
                else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { android.widget.Toast.makeText(context, "请在系统设置中开启取件码通知识别的通知访问权限", android.widget.Toast.LENGTH_LONG).show() }
            }
        }
        fun hasAccess(context: Context): Boolean {
            val component = ComponentName(context, SmsNotificationListener::class.java)
            // 8.1+ 查询通知管理器的实际授权，避免仅凭旧的 Secure 字符串判断。
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                return context.getSystemService(android.app.NotificationManager::class.java)
                    ?.isNotificationListenerAccessGranted(component) == true
            }
            return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
