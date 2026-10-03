package com.pickupcode.app.backup

import com.pickupcode.app.data.CodeHistory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BackupCodecTest {
    private fun payload(records: List<CodeHistory> = listOf(CodeHistory(id = 7, code = "7-3-5268", type = "pickup_parcel",
        source = "圆通", rawTextSnippet = "取件码7-3-5268", pickupAddress = "青禾村24排4号", timestamp = 100,
        isActive = false, archiveKind = "done", doneAt = 200, userEditedFields = 4, screenshotPath = "/private/screenshot.png"))) =
        BackupCodec.Payload(records, JSONObject().put("confidenceThreshold", 0.5).put("darkMode", "system"), JSONArray(),
            JSONObject().put("custom", JSONArray()).put("disabled", JSONArray()).put("edits", JSONObject()))

    @Test fun `portable backup round trips state and edits without device paths`() {
        val input = payload(); val json = BackupCodec.encode(input)
        assertFalse(json.contains("/private/screenshot.png")); assertFalse(json.contains("screenshotPath"))
        val restored = BackupCodec.decode(json)
        assertEquals(input.records.map { it.copy(id = 0, screenshotPath = "") }, restored.records)
    }
    @Test fun `invalid version type status and timestamps are rejected before restore`() {
        fun check(change: (JSONObject) -> Unit) {
            val json = JSONObject(BackupCodec.encode(payload())); change(json)
            assertThrows(Exception::class.java) { BackupCodec.decode(json.toString()) }
        }
        check { it.put("version", 2) }; check { it.put("format", "other-app") }
        check { it.getJSONArray("records").getJSONObject(0).put("type", "unknown") }
        check { it.getJSONArray("records").getJSONObject(0).put("timestamp", -1) }
        check { it.getJSONArray("records").getJSONObject(0).put("isActive", true) }
        check { it.getJSONObject("localSettings").put("confidenceThreshold", 2.0) }
        check { it.getJSONObject("rules").getJSONObject("edits").put("id", "[") }
    }
    @Test fun `corrupt and oversized backups are rejected`() {
        assertThrows(Exception::class.java) { BackupCodec.decode("{broken") }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(" ".repeat(BackupCodec.MAX_BYTES + 1)) }
    }
}
