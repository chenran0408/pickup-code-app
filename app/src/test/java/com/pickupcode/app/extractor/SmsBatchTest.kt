package com.pickupcode.app.extractor

import com.pickupcode.app.corpus.CorpusFixture
import com.pickupcode.app.ocr.OCREngine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** 用户批次的同形态脱敏真值，单条与拼接输入都通过生产提取/逐码上下文逻辑。 */
class SmsBatchTest {
    private val fixtures = CorpusFixture.loadAll(File("src/test/resources/corpus"))
        .filter { it.id.startsWith("sms-batch-") }
    private val now = Instant.parse("2026-10-02T01:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val cabinets = mapOf("800414" to "1号柜", "022571" to "1号柜", "712024" to "4号柜",
        "384114" to "3号柜", "7922-0881" to "7号柜", "6805-6767" to "6号柜", "251811" to "2号柜")
    private val free48h = setOf("800414", "022571", "712024", "384114")

    @TestFactory fun `individual and concatenated SMS keep independent fields`() = buildList {
        for (f in fixtures) add(DynamicTest.dynamicTest("individual ${f.id}") {
            verify(f.lines, listOf(f))
        })
        add(DynamicTest.dynamicTest("all messages on separate lines") {
            verify(fixtures.flatMap { it.lines }, fixtures)
        })
        val joined = fixtures.joinToString("") { it.lines.joinToString("\n") { line -> line.text } }
        add(DynamicTest.dynamicTest("all messages pasted into one line") {
            verify(listOf(OCREngine.TextLine(joined, null, 1f)), fixtures)
        })
        add(DynamicTest.dynamicTest("normalized brackets and fullwidth digits") {
            verify(listOf(OCREngine.TextLine(CodeExtractor.normalizeText(joined), null, 1f)), fixtures)
        })
        add(DynamicTest.dynamicTest("eight digit locker code needs explicit context") {
            assertTrue(CodeExtractor.extract(listOf(OCREngine.TextLine("7922-0881", null, 1f))).isEmpty())
            assertTrue(CodeExtractor.extract(listOf(OCREngine.TextLine("7号柜18-15号格口", null, 1f))).isEmpty())
        })
    }

    private fun verify(lines: List<OCREngine.TextLine>, expected: List<CorpusFixture>) {
        val results = CodeExtractor.extract(lines, source = "sms")
        assertEquals(expected.flatMap { it.expectedCodes }.map { it.code }.toSet(), results.map { it.code }.toSet())
        assertEquals(results.map { it.code }.distinct().size, results.size, "重复短信只返回一个码")
        for (f in expected) for (e in f.expectedCodes) {
            val got = results.single { it.code == e.code }
            assertEquals(e.type, got.type)
            assertTrue(got.source.contains(f.expectedCodeSources.single().source), "${e.code}: ${got.source}")
            val scoped = CodeContext.linesForCode(lines, e.code, results.map { it.code })
            val text = scoped.joinToString("\n") { it.text }
            val address = AddressExtractor.extractAddressForCode(scoped, e.code)
                .ifBlank { AddressExtractor.extractLocation(scoped, text).fullAddress }
            assertTrue(address.contains(f.expectedCodeAddresses.single().address), "${e.code}: $address")
            assertFalse(Regex("免费|超时|请凭|请20|取尾号|详询|取件码").containsMatchIn(address), "地址混入通知文案: $address")
            assertEquals(cabinets[e.code].orEmpty(), AddressExtractor.extractCabinetNumber(scoped, text), e.code)
            val deadline = when (e.code) {
                in free48h -> now + 48 * 3600000L
                "24-3-5116" -> Instant.parse("2026-10-02T12:00:00Z").toEpochMilli()
                else -> now + ExpiryExtractor.DEFAULT_PARCEL_LIFETIME_MS
            }
            assertEquals(deadline, ExpiryExtractor.expiryTimeFor(text, e.type, now, zone), e.code)
        }
    }
}
