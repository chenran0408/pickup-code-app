package com.pickupcode.app.performance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.room.withTransaction
import com.pickupcode.app.App
import com.pickupcode.app.MainActivity
import com.pickupcode.app.data.AppDatabase
import com.pickupcode.app.data.CodeHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 仅 debug 的 ADB 性能数据工具。只操作本工具创建且仍保持合成标记的记录，不导出用户正文。 */
class PerformanceFixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra("action") ?: "status"
        App.appScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@PerformanceFixtureActivity)
                val prefs = getSharedPreferences("performance_fixture", MODE_PRIVATE)
                val ids = prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
                val dao = db.codeHistoryDao()
                when (action) {
                    "seed" -> {
                        check(ids.isEmpty()) { "先清理旧的合成记录" }
                        val now = System.currentTimeMillis()
                        val created = db.withTransaction {
                            (1..144).map { i -> dao.insert(CodeHistory(
                                code = "88-1-${70000 + i}", type = "pickup_parcel", source = "圆通",
                                rawTextSnippet = MARKER, pickupAddress = "性能合成站点${(i - 1) / 12 + 1}",
                                timestamp = now - i * 60000L, isActive = i <= 120,
                                archiveKind = if (i <= 120) "" else "done", doneAt = if (i <= 120) 0 else now
                            )) }
                        }
                        check(prefs.edit().putStringSet("ids", created.map { it.toString() }.toSet()).commit())
                    }
                    "clean" -> {
                        db.withTransaction {
                            val owned = ids.filter { id -> dao.getByIdSuspend(id)?.let {
                                it.rawTextSnippet == MARKER && it.source == "圆通" &&
                                    it.pickupAddress.startsWith("性能合成站点") && it.screenshotPath.isEmpty() &&
                                    it.code.removePrefix("88-1-").toIntOrNull() in 70001..70144
                            } == true }
                            check(owned.size == ids.size) { "合成记录已被编辑，停止清理" }
                            if (owned.isNotEmpty()) dao.deleteByIds(owned)
                        }
                        check(prefs.edit().remove("ids").commit())
                    }
                    "status" -> Unit
                    else -> error("不支持的操作")
                }
                val stats = JSONObject()
                db.openHelper.readableDatabase.query(
                    "SELECT COUNT(*), SUM(CASE WHEN rawTextSnippet = ? THEN 1 ELSE 0 END), " +
                        "SUM(CASE WHEN rawTextSnippet != ? AND isActive = 0 THEN 1 ELSE 0 END) FROM code_history",
                    arrayOf(MARKER, MARKER)
                ).use { c ->
                    c.moveToFirst()
                    stats.put("total", c.getInt(0)).put("fixture", c.getInt(1)).put("originalArchived", c.getInt(2))
                }
                filesDir.resolve("performance-fixture-status.json").writeText(stats.toString())
                Log.i("PerformanceFixture", "PERF $action $stats")
            } catch (e: Exception) {
                Log.e("PerformanceFixture", "PERF FAILED ${e.javaClass.simpleName}")
            } finally {
                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@PerformanceFixtureActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
    companion object { private const val MARKER = "PERF_FIXTURE_20261003_V1" }
}
