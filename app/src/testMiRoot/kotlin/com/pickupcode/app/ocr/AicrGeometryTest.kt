package com.pickupcode.app.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AicrGeometryTest {
    @Test
    fun polygonCoordinatesBecomeAnImageRelativeEnvelope() {
        assertEquals(
            OCREngine.LineBox(12, 29, 143, 62),
            OCREngine.polygonBounds(floatArrayOf(12f, 30f, 142f, 29f, 143f, 61f, 13f, 62f), 200, 100)
        )
    }

    @Test
    fun malformedOrOutOfImagePolygonDoesNotAffectGeometricScoring() {
        assertNull(OCREngine.polygonBounds(floatArrayOf(1f, 2f, 3f), 200, 100))
        assertNull(OCREngine.polygonBounds(floatArrayOf(1f, 2f, 3f, 4f, 5f, 101f), 200, 100))
        assertNull(OCREngine.polygonBounds(floatArrayOf(4f, 4f, 4f, 4f, 4f, 4f), 200, 100))
    }
}
