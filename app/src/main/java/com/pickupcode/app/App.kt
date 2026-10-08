package com.pickupcode.app

import android.app.Application
import android.util.Log
import com.pickupcode.app.data.AppDatabase
import com.pickupcode.app.learner.CommonStationStore
import com.pickupcode.app.learner.PatternLearner
import com.pickupcode.app.notification.CodeNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        CodeNotificationManager.createChannels(this)
        restoreNotificationListener()
        cleanScreenshots()
    }

    private fun restoreNotificationListener() {
        appScope.launch(Dispatchers.IO) {
            // 部分系统覆盖安装后保留授权但不重新绑定；不在首页主线程读设置。
            runCatching {
                val selected = com.pickupcode.app.preferences.AppPreferences.observeNotificationApps(this@App)
                    .first()
                if (selected.isNotEmpty() && com.pickupcode.app.service.SmsNotificationListener.hasAccess(this@App)) {
                    android.service.notification.NotificationListenerService.requestRebind(
                        android.content.ComponentName(this@App, com.pickupcode.app.service.SmsNotificationListener::class.java))
                }
            }.onFailure { Log.w("App", "恢复通知识别连接失败", it) }
        }
    }

    /**
     * 截图治理（代码检查 3-3：截图明文落盘且无 TTL）：
     * 启动时在 IO 线程跑一次「孤儿清扫 + 30 天硬保留期 + 50MB 总量上限」。
     * 放后台、捕获全部异常——清理失败绝不该影响启动。
     */
    private fun cleanScreenshots() {
        appScope.launch(Dispatchers.IO) {
            try {
                CommonStationStore.migrateLegacyAddresses(this@App)
                PatternLearner.migrateLegacySamples(this@App)
                // 加密迁移仍立即进行；截图治理稍后执行，避免与首页首次读库争用数据库和文件系统。
                delay(2_000)
                AppDatabase.getInstance(this@App).repository.cleanScreenshots(this@App)
            } catch (e: Exception) {
                Log.w("App", "截图治理失败", e)
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set

        // 全局协程作用域：供分享识别等「不依赖 Activity 生命周期」的后台任务使用，
        // 避免 Activity 销毁中断正在处理的识别流程（截图/OCR/DB写入）。
        val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    }
}
