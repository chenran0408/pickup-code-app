package com.pickupcode.app.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NotificationConnectionRecoveryTest {
    @Test fun `stale binding reset occurs once after ordinary rebind times out`() = runBlocking {
        val resets = mutableListOf<Boolean>()
        val result = recoverNotificationConnection({ true }, { false }, { resets += it }, {})
        assertEquals(NotificationConnectionResult.FAILED, result)
        assertEquals(listOf(false, true, false), resets)
    }

    @Test fun `successful ordinary rebind never resets listener`() = runBlocking {
        var connected = false
        val resets = mutableListOf<Boolean>()
        val result = recoverNotificationConnection({ true }, { connected }, { resets += it }, { connected = true })
        assertEquals(NotificationConnectionResult.CONNECTED, result)
        assertEquals(listOf(false), resets)
    }

    @Test fun `request returning does not imply connection and attempts are bounded`() = runBlocking {
        var requests = 0
        val result = recoverNotificationConnection({ true }, { false }, { requests++ }, {})
        assertEquals(NotificationConnectionResult.FAILED, result)
        assertEquals(3, requests)
    }

    @Test fun `connection callback stops retrying`() = runBlocking {
        var requests = 0
        var connected = false
        val result = recoverNotificationConnection({ true }, { connected }, { requests++ }, { connected = true })
        assertEquals(NotificationConnectionResult.CONNECTED, result)
        assertEquals(1, requests)
    }

    @Test fun `already connected does not disturb live listener`() = runBlocking {
        val result = recoverNotificationConnection({ true }, { true }, { fail("Must not rebind") }, {})
        assertEquals(NotificationConnectionResult.CONNECTED, result)
    }

    @Test fun `revoking access or clearing selection during retry stops recovery`() = runBlocking {
        var enabled = true
        var requests = 0
        val result = recoverNotificationConnection({ enabled }, { false }, { requests++ }, { enabled = false })
        assertEquals(NotificationConnectionResult.INACTIVE, result)
        assertEquals(1, requests)
    }

    @Test fun `disabled sources never request binding`() = runBlocking {
        assertEquals(NotificationConnectionResult.INACTIVE,
            recoverNotificationConnection({ false }, { false }, { fail("Must not rebind") }, {}))
    }

    @Test fun `cancellation must not turn into connection failure`() {
        assertThrows(CancellationException::class.java) {
            runBlocking { recoverNotificationConnection({ true }, { false }, { throw CancellationException() }, {}) }
        }
    }

    @Test fun `status distinguishes permission connection and disabled sources`() {
        val failed = NotificationRecognitionStatus.State(connection = NotificationRecognitionStatus.Connection.FAILED)
        assertEquals("未选择应用，通知识别已关闭", NotificationRecognitionStatus.description(0, true, failed))
        assertEquals("请开启通知访问权限", NotificationRecognitionStatus.description(2, false, failed))
        assertTrue(NotificationRecognitionStatus.description(2, true, failed).startsWith("系统未连接"))
        assertEquals("通知识别已连接", NotificationRecognitionStatus.description(2, true, failed.copy(connected = true)))
    }
}
