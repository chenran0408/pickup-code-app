package com.pickupcode.app.aicr

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AicrBarcodeFormatTest {
    @Test
    fun couponScannerAcceptsQrButNotLinearCodes() {
        assertTrue(AicrBarcodeClient.isQrFormat(256))
        assertFalse(AicrBarcodeClient.isQrFormat(1))
    }
}
