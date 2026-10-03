package com.pickupcode.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 统一数据仓库——三条识别路径的唯一数据入口。
 * 替代各路径直接调 DAO 的模式，去重/合并逻辑集中管理。
 */
class CodeRepository(private val dao: CodeHistoryDao) {
    private val screenshotMutex = Mutex()
    private val screenshotLeases = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun retainScreenshot(path: String) {
        if (path.isNotBlank()) screenshotLeases.compute(path) { _, count -> (count ?: 0) + 1 }
    }

    suspend fun releaseScreenshotLease(path: String) = screenshotMutex.withLock {
        if (path.isNotBlank()) {
            screenshotLeases.computeIfPresent(path) { _, count -> (count - 1).takeIf { it > 0 } }
        }
    }

    suspend fun findMergeTarget(history: CodeHistory): CodeHistory? =
        dao.mergeCandidates(history.code, history.type).firstOrNull { RecordMergePolicy.samePickup(it, history) }


    suspend fun save(history: CodeHistory): CodeHistoryDao.SaveResult =
        screenshotMutex.withLock { dao.saveOrUpdate(history) }

    suspend fun findByCodeAndType(code: String, type: String): CodeHistory? =
        dao.findByCodeAndType(code, type)

    suspend fun markDoneByCodeAndType(code: String, type: String) =
        dao.markDoneByCodeAndType(code, type)

    suspend fun restore(id: Long) = dao.restore(id)

    fun observeActive(): Flow<List<CodeHistory>> = dao.getActiveFlow()

    fun observeCompleted(): Flow<List<CodeHistory>> = dao.getCompletedFlow()
    suspend fun getAll(): List<CodeHistory> = dao.getAll()
    suspend fun importRecords(records: List<CodeHistory>): Int = screenshotMutex.withLock { dao.importRecords(records) }
    suspend fun moveToTrash(id: Long) = dao.moveToTrash(id, System.currentTimeMillis())

    fun observeTrash(): Flow<List<CodeHistory>> = dao.getTrashFlow()

    suspend fun countDuplicateGroups(): Int = dao.countDuplicateGroups()

    suspend fun updatePickupAddress(id: Long, address: String) =
        dao.updatePickupAddress(id, address)

    suspend fun updateGeo(id: Long, verified: Boolean, confidence: Float, formatted: String) =
        dao.updateGeo(id, verified, confidence, formatted)

    suspend fun updateCode(id: Long, code: String) = dao.updateCode(id, code)

    suspend fun updateSource(id: Long, source: String) = dao.updateSource(id, source)

    suspend fun updateCabinet(id: Long, cabinet: String) = dao.updateCabinet(id, cabinet)

    suspend fun cleanExpired(before: Long) = screenshotMutex.withLock {
        val paths = dao.getExpiredScreenshots(before)
        dao.deleteExpiredTrash(before)
        paths.distinct().forEach { deleteIfUnreferenced(it) }
    }

    suspend fun releaseScreenshots(paths: List<String>) = screenshotMutex.withLock {
        paths.distinct().forEach { deleteIfUnreferenced(it) }
    }

    private suspend fun deleteIfUnreferenced(path: String) {
        if (path.isNotBlank() && !screenshotLeases.containsKey(path) && dao.screenshotReferences(path) == 0) deleteFileQuietly(path)
    }

    data class DoneBatch(val items: List<CodeHistory>, val doneAt: Long)

    suspend fun markDoneBatch(ids: List<Long>): DoneBatch {
        return dao.archiveBatch(ids, System.currentTimeMillis())
    }

    suspend fun restoreBatch(batch: DoneBatch) = dao.restoreBatch(batch.items.map { it.id }, batch.doneAt)
    suspend fun protectField(id: Long, field: Int) = dao.protectField(id, field)
    suspend fun fillAddressIfBlank(id: Long, address: String) = dao.fillAddressIfBlank(id, address)
    suspend fun updateGeoIfCurrent(id: Long, address: String, verified: Boolean, confidence: Float, formatted: String) =
        dao.updateGeoIfCurrent(id, address, verified, confidence, formatted)

    /** AI 回填只作用于本次记录，已经取走或编辑码值的记录不再接受旧任务结果。 */
    suspend fun enrichIfCurrent(id: Long, fresh: CodeHistory): Boolean = screenshotMutex.withLock {
        dao.enrichIfCurrent(id, fresh)
    }

    /**
     * 截图治理：孤儿清扫 + 硬保留期 + 目录总量上限（见 [ScreenshotStore]）。
     * 被删掉但仍被记录引用的路径会同步清空 DB 引用，避免详情页指向不存在的文件。
     * @return 实际删除的文件数
     */
    suspend fun cleanScreenshots(
        context: android.content.Context,
        now: Long = System.currentTimeMillis()
    ): Int = screenshotMutex.withLock {
        val referenced = dao.getAllScreenshotPaths().toSet() + screenshotLeases.keys
        val deleted = com.pickupcode.app.util.ScreenshotStore.sweep(context, referenced, now)
        if (deleted.isNotEmpty()) {
            val stillReferenced = deleted.filter { it in referenced }
            if (stillReferenced.isNotEmpty()) dao.clearScreenshotPaths(stillReferenced)
        }
        deleted.size
    }

    suspend fun findSameCodeDifferentType(code: String, type: String): List<CodeHistory> =
        dao.findSameCodeDifferentType(code, type)

    suspend fun countActiveByCodeAndType(code: String, type: String): Int =
        dao.countActiveByCodeAndType(code, type)

    /** 删除记录并回收其截图文件（避免 cacheDir 孤儿，代码检查 3-14）。 */
    suspend fun deleteByIds(ids: List<Long>) = screenshotMutex.withLock {
        if (ids.isEmpty()) return@withLock
        val paths = dao.getScreenshotPathsByIds(ids)
        dao.deleteByIds(ids)
        paths.distinct().forEach { deleteIfUnreferenced(it) }
    }

    suspend fun deleteById(id: Long) = screenshotMutex.withLock {
        val paths = dao.getScreenshotPathsByIds(listOf(id))
        dao.deleteById(id)
        paths.distinct().forEach { deleteIfUnreferenced(it) }
    }

    /** 删文件不抛异常：图片缺失只影响详情页预览，不该让删除记录失败。 */
    private fun deleteFileQuietly(path: String) {
        if (path.isBlank()) return
        try {
            java.io.File(path).delete()
        } catch (_: Exception) {
        }
    }

    fun getById(id: Long): Flow<CodeHistory?> = dao.getById(id)

    suspend fun getByIdSuspend(id: Long): CodeHistory? = dao.getByIdSuspend(id)

    suspend fun getDuplicateEntries(): List<CodeHistory> = dao.getDuplicateEntries()

    suspend fun markDone(id: Long, doneAt: Long = System.currentTimeMillis()) =
        dao.markDone(id, doneAt)


}
