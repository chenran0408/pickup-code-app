package com.pickupcode.app.data

/** 同码并不等于同一订单；只有上下文足够明确时才合并。 */
object RecordMergePolicy {
    const val CODE = 1
    const val SOURCE = 2
    const val ADDRESS = 4
    const val CABINET = 8

    private fun normalized(value: String) = value.trim().replace(Regex("\\s+"), "")
    private fun knownSource(value: String) = value.isNotBlank() && value !in setOf("unknown", "未知", "未知来源")

    fun samePickup(old: CodeHistory, fresh: CodeHistory): Boolean {
        if (!old.isActive || old.code != fresh.code || old.type != fresh.type) return false
        if (old.trackingNumber.isNotBlank() && fresh.trackingNumber.isNotBlank()) {
            return old.trackingNumber == fresh.trackingNumber
        }
        val age = kotlin.math.abs(fresh.timestamp - old.timestamp)
        val maxAge = if (fresh.type == "pickup_food") 4 * 60 * 60_000L else 7 * 24 * 60 * 60_000L
        if (age > maxAge) return false
        if (old.cabinetNumber.isNotBlank() && fresh.cabinetNumber.isNotBlank() &&
            normalized(old.cabinetNumber) != normalized(fresh.cabinetNumber)) return false
        val a = AddressNormalizer.key(old.pickupAddress)
        val b = AddressNormalizer.key(fresh.pickupAddress)
        if (a.isNotEmpty() && b.isNotEmpty() && a != b) return false
        val oldSource = knownSource(old.source)
        val newSource = knownSource(fresh.source)
        if (oldSource && newSource && normalized(old.source) != normalized(fresh.source)) return false
        if (a.isNotEmpty() && a == b) return true
        if (old.screenshotPath.isNotBlank() && old.screenshotPath == fresh.screenshotPath) return true
        if (oldSource && newSource && old.source == fresh.source) {
            val specificPlace = old.source.length >= 4 && old.source !in setOf("菜鸟驿站", "丰巢快递柜") &&
                Regex("店|驿站|号柜|代收点").containsMatchIn(old.source)
            if (specificPlace || (old.recognitionOrigin == "user" && fresh.recognitionOrigin == "user")) {
                return age <= 10 * 60_000L
            }
        }
        // 品牌名或裸码文本不足以确认门店；完整上下文重复才可作为保守证据。
        return old.rawTextSnippet.length >= 20 &&
            old.rawTextSnippet == fresh.rawTextSnippet && age <= 10 * 60_000L
    }

    fun merge(old: CodeHistory, fresh: CodeHistory): CodeHistory {
        fun field(bit: Int, previous: String, incoming: String): String =
            if (old.userEditedFields and bit != 0 || incoming.isBlank()) previous else incoming
        // AI 的不同地址先保存为候选，用户确认的字段不受自动识别影响。
        val aiConflict = fresh.addressOrigin == "ai" && old.pickupAddress.isNotBlank() &&
            normalized(old.pickupAddress) != normalized(fresh.pickupAddress)
        val address = if (aiConflict) old.pickupAddress else field(ADDRESS, old.pickupAddress, fresh.pickupAddress)
        val addressChanged = address != old.pickupAddress
        return old.copy(
            source = field(SOURCE, old.source, fresh.source.takeUnless { it == "unknown" }.orEmpty()),
            pickupAddress = address,
            cabinetNumber = field(CABINET, old.cabinetNumber, fresh.cabinetNumber),
            screenshotPath = fresh.screenshotPath.ifBlank { old.screenshotPath },
            rawTextSnippet = fresh.rawTextSnippet.ifBlank { old.rawTextSnippet },
            shareSourcePkg = fresh.shareSourcePkg.ifBlank { old.shareSourcePkg },
            shareSourceName = fresh.shareSourceName.ifBlank { old.shareSourceName },
            trackingNumber = fresh.trackingNumber.ifBlank { old.trackingNumber },
            expiryTime = if (fresh.expiryTime > 0) minOf(old.expiryTime.takeIf { it > 0 } ?: fresh.expiryTime, fresh.expiryTime) else old.expiryTime,
            geoVerified = if (addressChanged) false else old.geoVerified,
            geoConfidence = if (addressChanged) 0f else old.geoConfidence,
            geoFormattedAddress = if (addressChanged) "" else old.geoFormattedAddress,
            addressOrigin = if (addressChanged) fresh.addressOrigin else old.addressOrigin,
            recognitionOrigin = if (old.recognitionOrigin == "ai" && fresh.recognitionOrigin != "ai") fresh.recognitionOrigin else old.recognitionOrigin,
            suggestedAddress = if (aiConflict) fresh.pickupAddress else fresh.suggestedAddress.ifBlank { old.suggestedAddress }
        )
    }
}
