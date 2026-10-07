package com.pickupcode.app.data

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.file.Files

class CodeRepositoryTest {
    private class MemoryDao : CodeHistoryDao by unsupportedDao() {
        val rows = linkedMapOf<Long, CodeHistory>()
        override suspend fun getByIdSuspend(id: Long) = rows[id]
        override suspend fun mergeCandidates(code: String, type: String) = rows.values.filter { it.isActive && it.code == code && it.type == type }
        override suspend fun screenshotReferences(path: String) = rows.values.count { it.screenshotPath == path }
        override suspend fun insert(history: CodeHistory): Long {
            val id = (rows.keys.maxOrNull() ?: 0) + 1
            rows[id] = history.copy(id = id)
            return id
        }
        override suspend fun update(history: CodeHistory) { rows[history.id] = history }
        override suspend fun saveOrUpdate(history: CodeHistory) = super<CodeHistoryDao>.saveOrUpdate(history)
        override suspend fun archiveBatch(ids: List<Long>, doneAt: Long) = super<CodeHistoryDao>.archiveBatch(ids, doneAt)
        override suspend fun enrichIfCurrent(id: Long, fresh: CodeHistory) = super<CodeHistoryDao>.enrichIfCurrent(id, fresh)
        override suspend fun importPendingBatch(records: List<CodeHistory>) = super<CodeHistoryDao>.importPendingBatch(records)
        override suspend fun getScreenshotPathsByIds(ids: List<Long>) = ids.mapNotNull { rows[it]?.screenshotPath }.filter { it.isNotBlank() }
        override suspend fun deleteById(id: Long) { rows.remove(id) }
        override suspend fun deleteByIds(ids: List<Long>) { ids.forEach { rows.remove(it) } }
        override suspend fun activeByIds(ids: List<Long>) = ids.mapNotNull { rows[it] }.filter { it.isActive }
        override suspend fun markDoneByIds(ids: List<Long>, doneAt: Long) {
            ids.forEach { id -> rows[id]?.takeIf { it.isActive }?.let { rows[id] = it.copy(isActive = false, doneAt = doneAt) } }
        }
        override suspend fun restoreBatch(ids: List<Long>, doneAt: Long) {
            ids.forEach { id -> rows[id]?.takeIf { !it.isActive && it.doneAt == doneAt }?.let { rows[id] = it.copy(isActive = true, doneAt = 0) } }
        }
        override suspend fun getExpiredScreenshots(before: Long) = rows.values.filter { !it.isActive && it.doneAt in 1 until before }.map { it.screenshotPath }
        override suspend fun deleteExpiredTrash(before: Long) {
            rows.values.filter { !it.isActive && it.doneAt in 1 until before }.map { it.id }.forEach { rows.remove(it) }
        }
    }

    companion object {
        private fun unsupportedDao() = Proxy.newProxyInstance(CodeHistoryDao::class.java.classLoader, arrayOf(CodeHistoryDao::class.java)) { _, method, _ ->
            error("Unexpected DAO call: ${method.name}")
        } as CodeHistoryDao
    }

    private fun record(code: String, address: String = "长兴路店", screenshot: String = "") =
        CodeHistory(code = code, type = "pickup_parcel", source = "中通", pickupAddress = address,
            rawTextSnippet = "取件码$code", screenshotPath = screenshot, timestamp = 100_000)

    @Test fun `deleting one record retains screenshot until last reference is deleted`() = runBlocking {
        val file = Files.createTempFile("pickup-shared-", ".jpg").toFile()
        try {
            val dao = MemoryDao(); val repo = CodeRepository(dao)
            val first = repo.save(record("3-7-4162", screenshot = file.path)).id
            val second = repo.save(record("9-4-1526", screenshot = file.path)).id
            repo.deleteById(first)
            assertTrue(file.exists())
            repo.deleteById(second)
            assertFalse(file.exists())
        } finally { file.delete() }
    }

    @Test fun `replacement cleanup retains screenshots referenced by another record`() = runBlocking {
        val old = Files.createTempFile("pickup-old-", ".jpg").toFile()
        val fresh = Files.createTempFile("pickup-new-", ".jpg").toFile()
        try {
            val dao = MemoryDao(); val repo = CodeRepository(dao)
            repo.save(record("3-7-4162", screenshot = old.path))
            repo.save(record("9-4-1526", screenshot = old.path))
            val result = repo.save(record("3-7-4162", screenshot = fresh.path))
            repo.releaseScreenshots(listOf(result.replacedScreenshotPath))
            assertTrue(old.exists())
            assertTrue(fresh.exists())
        } finally { old.delete(); fresh.delete() }
    }

    @Test fun `expiry cleanup respects active screenshot references`() = runBlocking {
        val file = Files.createTempFile("pickup-expired-", ".jpg").toFile()
        try {
            val dao = MemoryDao(); val repo = CodeRepository(dao)
            val old = repo.save(record("3-7-4162", screenshot = file.path)).id
            repo.save(record("9-4-1526", screenshot = file.path))
            dao.rows[old] = dao.rows.getValue(old).copy(isActive = false, doneAt = 100)
            repo.cleanExpired(200)
            assertTrue(file.exists())
            assertEquals(1, dao.rows.size)
        } finally { file.delete() }
    }

    @Test fun `undo restores only captured batch and does not affect new pickups`() = runBlocking {
        val dao = MemoryDao(); val repo = CodeRepository(dao)
        val first = repo.save(record("3-7-4162")).id
        val elsewhere = repo.save(record("3-7-4162", "长青街店")).id
        val batch = repo.markDoneBatch(listOf(first))
        val addedLater = repo.save(record("3-7-4162")).id
        repo.restoreBatch(batch)
        assertTrue(dao.rows.getValue(first).isActive)
        assertTrue(dao.rows.getValue(elsewhere).isActive)
        assertTrue(dao.rows.getValue(addedLater).isActive)
        assertEquals(listOf(first), batch.items.map { it.id })
    }

    @Test fun `late AI cannot update archived or edited records`() = runBlocking {
        val dao = MemoryDao(); val repo = CodeRepository(dao)
        val original = record("3-7-4162")
        val id = repo.save(original).id
        repo.markDoneBatch(listOf(id))
        assertFalse(repo.enrichIfCurrent(id, original.copy(source = "AI品牌")))
        dao.rows[id] = dao.rows.getValue(id).copy(isActive = true, code = "9-4-1526")
        assertFalse(repo.enrichIfCurrent(id, original.copy(source = "AI品牌")))
    }

    @Test fun `in-flight AI lease protects screenshot with no remaining database references`() = runBlocking {
        val file = Files.createTempFile("pickup-flight-", ".jpg").toFile()
        try {
            val dao = MemoryDao(); val repo = CodeRepository(dao)
            val id = repo.save(record("3-7-4162", screenshot = file.path)).id
            repo.retainScreenshot(file.path)
            repo.deleteById(id)
            assertTrue(file.exists())
            repo.releaseScreenshotLease(file.path)
            repo.releaseScreenshots(listOf(file.path))
            assertFalse(file.exists())
        } finally { file.delete() }
    }

    @Test fun `import is repeatable without overwriting and keeps different addresses`() = runBlocking {
        val dao = MemoryDao(); val repo = CodeRepository(dao)
        val original = record("7-3-5268", "青禾村24排4号").copy(rawTextSnippet = "用户原记录")
        val otherAddress = original.copy(pickupAddress = "青禾村24排5号", rawTextSnippet = "分享文本导入")
        assertEquals(1 to 0, repo.importPendingBatch(listOf(original)))
        assertEquals(1 to 1, repo.importPendingBatch(listOf(original.copy(rawTextSnippet = "不应覆盖"), otherAddress)))
        assertEquals("用户原记录", dao.rows.getValue(1).rawTextSnippet)
        assertEquals(2, dao.rows.size)
    }
}
