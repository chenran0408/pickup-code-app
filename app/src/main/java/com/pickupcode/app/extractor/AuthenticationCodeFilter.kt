package com.pickupcode.app.extractor

/** 验证码属于账户安全消息，不作为取件码。所有自动提取及入库路径共用此判断。 */
object AuthenticationCodeFilter {
    private val pickupGap = Regex("[\\s:：为是]*")
    private val authenticationLabel = Regex(
        "验证码|校验码|动态(?:密码|口令)|一次性(?:密码|口令)|验证代码|" +
            "\\b(?:OTP|verification\\s+code|security\\s+code|login\\s+code|one[ -]time\\s+(?:password|code))\\b",
        RegexOption.IGNORE_CASE)
    private val pickupLabel = Regex("取[件餐货单](?:码|号)|提取码|提货码|领取码|券号|券码|兑换码|凭\\s*[A-Za-z0-9-]{2,12}\\s*(?:到|至|取|领)")
    private val precedingPickupLabel = Regex("取[件餐货单](?:码|号)|提取码|提货码|领取码|券号|券码|兑换码|凭\\s*$")

    fun isAuthenticationOnly(text: String): Boolean {
        val normalized = CodeExtractor.normalizeText(text)
        return authenticationLabel.containsMatchIn(normalized) && !pickupLabel.containsMatchIn(normalized)
    }

    fun reject(code: String, text: String): Boolean {
        val normalized = CodeExtractor.normalizeText(text)
        if (!authenticationLabel.containsMatchIn(normalized)) return false
        if (isAuthenticationOnly(normalized)) return true
        val value = CodeExtractor.normalizeText(code).trim()
        if (value.isEmpty()) return false
        val occurrences = Regex("(?<![A-Za-z0-9])" + Regex.escape(value) + "(?![A-Za-z0-9])")
            .findAll(normalized).toList()
        // 同文多码时按标签归属判定，允许真正取件码与验证码同时出现；同值语义冲突时保守拒绝。
        return occurrences.any { match ->
            val before = normalized.substring(maxOf(0, match.range.first - 120), match.range.first)
            val auth = authenticationLabel.findAll(before).lastOrNull()
            val pickup = precedingPickupLabel.findAll(before).lastOrNull()
            val directPickup = pickup != null && pickupGap
                .matches(before.substring(pickup.range.last + 1))
            val followsAuth = auth != null && (pickup == null || auth.range.first > pickup.range.first)
            val after = normalized.substring(match.range.last + 1, minOf(normalized.length, match.range.last + 41))
            val trailingAuth = authenticationLabel.find(after)?.let { label ->
                val gap = after.substring(0, label.range.first)
                gap.length <= 16 && gap.all { it.isWhitespace() || it in "，,:：。()（）是为的您本次登录安全验证" }
            } == true
            followsAuth || (trailingAuth && !directPickup)
        }
    }
}
