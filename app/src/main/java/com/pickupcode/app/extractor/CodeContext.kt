package com.pickupcode.app.extractor

import com.pickupcode.app.ocr.OCREngine

object CodeContext {
    private val messageHeader = Regex("[【\\[][^】\\]\\n]{0,20}(?:快递|速递|物流|驿站|代收点|兔喜生活|邮侠|百米需)[^】\\]\\n]*[】\\]]")

    /** 无坐标短信/分享文本按发件方标题分段，截图仍使用卡片几何窗口。 */
    fun messageBlocks(lines: List<OCREngine.TextLine>): List<List<OCREngine.TextLine>> {
        if (lines.any { it.boundingBox != null }) return listOf(lines)
        val blocks = mutableListOf<MutableList<OCREngine.TextLine>>()
        var current = mutableListOf<OCREngine.TextLine>()
        for (line in lines) {
            val starts = messageHeader.findAll(line.text).map { it.range.first }.toList()
            var start = 0
            for (index in starts) {
                if (index > start) current.add(line.copy(text = line.text.substring(start, index)))
                if (current.isNotEmpty()) blocks.add(current)
                current = mutableListOf()
                start = index
            }
            if (start < line.text.length) current.add(line.copy(text = line.text.substring(start)))
        }
        if (current.isNotEmpty()) blocks.add(current)
        // 只有一个通知标题时保留其跨行上下文（包括“转自”说明）。
        return if (lines.sumOf { messageHeader.findAll(it.text).count() } > 1) blocks else listOf(lines)
    }

    fun linesForCode(lines: List<OCREngine.TextLine>, code: String, codes: List<String>): List<OCREngine.TextLine> {
        fun hasCode(text: String, value: String) = Regex("(?<![A-Za-z0-9])" + Regex.escape(value) + "(?![A-Za-z0-9])").containsMatchIn(CodeExtractor.normalizeText(text))
        val blocks = messageBlocks(lines)
        if (blocks.size > 1) {
            val ownBlock = blocks.firstOrNull { block -> block.any { hasCode(it.text, code) } } ?: return emptyList()
            return linesForCode(ownBlock, code, codes.filter { value -> ownBlock.any { hasCode(it.text, value) } })
        }
        if (codes.distinct().size <= 1) return lines
        val anchors = codes.distinct().mapNotNull { value ->
            lines.indexOfFirst { hasCode(it.text, value) }.takeIf { it >= 0 }?.let { value to it }
        }
        val own = anchors.firstOrNull { it.first == code }?.second ?: return emptyList()
        val top = lines[own].boundingBox?.top
        return lines.filterIndexed { i, line ->
            val nearest = anchors.minByOrNull { (_, index) ->
                val y = lines[index].boundingBox?.top
                if (y != null && line.boundingBox != null) kotlin.math.abs(line.boundingBox.top - y).toLong()
                else kotlin.math.abs(i - index).toLong()
            }
            val distanceOk = kotlin.math.abs(i - own) <= 3 &&
                (top == null || line.boundingBox == null || kotlin.math.abs(line.boundingBox.top - top) <= 400)
            val tied = anchors.count { (_, index) ->
                val y = lines[index].boundingBox?.top
                if (y != null && line.boundingBox != null && top != null)
                    kotlin.math.abs(line.boundingBox.top - y) == kotlin.math.abs(line.boundingBox.top - top)
                else kotlin.math.abs(i - index) == kotlin.math.abs(i - own)
            } > 1
            distanceOk && nearest?.first == code && (!tied || i == own) &&
                anchors.none { (value, _) -> value != code && hasCode(line.text, value) }
        }
    }
}
