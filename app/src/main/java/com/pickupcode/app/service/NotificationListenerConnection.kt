package com.pickupcode.app.service

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import com.pickupcode.app.App
import com.pickupcode.app.preferences.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 启动、返回前台、选择来源和断连共用恢复流程，避免重复重绑和无限后台重试。 */
object NotificationListenerConnection {
    private var recovery: Job? = null // 只在主线程访问

    fun recover(context: Context, selectionChanged: Boolean = false) {
        val app = context.applicationContext
        App.appScope.launch(Dispatchers.Main.immediate) {
            if (selectionChanged) recovery?.cancel()
            if (recovery?.isActive == true) return@launch
            recovery = launch {
                try {
                    val result = recoverNotificationConnection(
                        enabled = {
                            val (selected, granted) = withContext(Dispatchers.IO) {
                                AppPreferences.observeNotificationApps(app).first().isNotEmpty() to SmsNotificationListener.hasAccess(app)
                            }
                            when {
                                !selected -> NotificationRecognitionStatus.connection(NotificationRecognitionStatus.Connection.OFF)
                                !granted -> {
                                    NotificationRecognitionStatus.connected(false)
                                    NotificationRecognitionStatus.connection(NotificationRecognitionStatus.Connection.NO_ACCESS)
                                }
                            }
                            selected && granted
                        },
                        connected = { NotificationRecognitionStatus.state.value.connected },
                        request = { resetStaleBinding ->
                            NotificationRecognitionStatus.connection(NotificationRecognitionStatus.Connection.CONNECTING)
                            // 解绑与重绑必须成对完成，来源变化取消旧任务时也不能遗留系统 snoozed 状态。
                            withContext(Dispatchers.IO + NonCancellable) {
                                if (!NotificationRecognitionStatus.state.value.connected) {
                                    val component = ComponentName(app, SmsNotificationListener::class.java)
                                    try {
                                        if (resetStaleBinding && android.os.Build.VERSION.SDK_INT >= 34) {
                                            NotificationListenerService.requestUnbind(component)
                                        }
                                    } finally {
                                        NotificationListenerService.requestRebind(component)
                                    }
                                }
                            }
                        },
                        awaitConnection = {
                            withTimeoutOrNull(5_000) { NotificationRecognitionStatus.state.first { it.connected } }
                            Unit
                        },
                    )
                    when (result) {
                        NotificationConnectionResult.CONNECTED -> NotificationRecognitionStatus.connection(NotificationRecognitionStatus.Connection.CONNECTED)
                        NotificationConnectionResult.FAILED -> {
                            NotificationRecognitionStatus.connection(NotificationRecognitionStatus.Connection.FAILED)
                            Log.w("NotificationConnection", "通知服务未收到连接回调，自动恢复已停止")
                        }
                        NotificationConnectionResult.INACTIVE -> Unit
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    NotificationRecognitionStatus.connection(NotificationRecognitionStatus.Connection.FAILED)
                    Log.w("NotificationConnection", "通知连接恢复失败: ${e.javaClass.simpleName}")
                }
            }
        }
    }
}
