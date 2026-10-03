package com.pickupcode.app.feedback

/** 自动替换数字，用户在导出前编辑姓名与地址；从不自动发送反馈。 */
object FeedbackSanitizer {
    fun maskNumbers(text: String): String {
        val random = java.security.SecureRandom()
        val replacements = mutableMapOf<String, String>()
        return Regex("[0-9０-９]+").replace(text.take(20000)) { match ->
            replacements.getOrPut(match.value) {
                val fake = match.value.map { char ->
                    (if (char in '０'..'９') '０' else '0') + random.nextInt(10)
                }.joinToString("")
                if (fake == match.value) {
                    val first = fake.first()
                    (if (first == '9') '0' else if (first == '９') '０' else first + 1) + fake.drop(1)
                } else fake
            }
        }
    }

    fun report(kind: String, source: String, text: String, expected: String, version: String): String =
        "码上闪记识别反馈\n版本：$version\n问题：$kind\n入口：$source\n期望结果：${expected.take(2000)}\n脱敏样例：\n${text.take(20000)}"
}
