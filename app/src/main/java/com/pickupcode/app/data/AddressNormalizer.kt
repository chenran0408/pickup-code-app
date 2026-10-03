package com.pickupcode.app.data

/** 只合并全半角、空白和末尾句读差异；不猜测别名，避免把不同地址合并。 */
object AddressNormalizer {
    private val whitespace = Regex("[\\s\\u00a0\\u3000]+")
    fun key(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
        .replace(whitespace, "").trimEnd('。', '.', '，', ',', '；', ';')
}
