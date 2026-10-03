package com.pickupcode.app.extractor

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AICancellationTest {
    private class SlowConnection : HttpURLConnection(URL("https://example.invalid")) {
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        val disconnected = AtomicBoolean(false)
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected.set(true); released.countDown() }
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getResponseCode(): Int {
            started.countDown()
            released.await(10, TimeUnit.SECONDS)
            return 200
        }
        override fun getInputStream() = ByteArrayInputStream("{}".toByteArray())
    }

    @Test fun `cancelling coroutine disconnects blocked HTTP request`() = runBlocking {
        val connection = SlowConnection()
        val request = async {
            AIExtractor.postJson("{}", "synthetic-test-key", "https://example.invalid/v1", 30_000) { connection }
        }
        withTimeout(2_000) {
            while (connection.started.count > 0) kotlinx.coroutines.delay(10)
        }
        request.cancel()
        withTimeout(2_000) { request.join() }
        assertTrue(connection.disconnected.get())
        assertEquals(0L, connection.released.count)
    }

    @Test fun `timeout returns promptly and closes connection`() = runBlocking {
        val connection = SlowConnection()
        val result = withTimeoutOrNull(300) {
            AIExtractor.postJson("{}", "synthetic-test-key", "https://example.invalid/v1", 30_000) { connection }
        }
        assertNull(result)
        assertTrue(connection.disconnected.get())
    }
}
