package com.pickupcode.app.share

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShareRecognitionSessionTest {
    @Test fun `older image cannot overwrite a newer share`() {
        val session = ShareRecognitionSession()
        val older = session.start()
        val newer = session.start()
        session.complete(older, message = "old")
        assertEquals(newer, session.feedback.value?.id)
        assertTrue(session.feedback.value!!.processing)
        session.complete(newer, message = "new")
        assertEquals("new", session.feedback.value?.message)
        assertFalse(session.feedback.value!!.processing)
    }
    @Test fun `background recognition does not reopen dismissed feedback`() {
        val session = ShareRecognitionSession()
        val id = session.start()
        session.dismiss(id)
        session.complete(id, message = "done")
        assertNull(session.feedback.value)
        val newer = session.start()
        session.dismiss(id)
        assertEquals(newer, session.feedback.value?.id)
    }
    @Test fun `results remain available until explicitly dismissed`() {
        val session = ShareRecognitionSession()
        val id = session.start()
        session.complete(id, message = "No code")
        assertEquals("No code", session.feedback.value?.message)
        session.dismiss(id)
        assertNull(session.feedback.value)
    }
}
