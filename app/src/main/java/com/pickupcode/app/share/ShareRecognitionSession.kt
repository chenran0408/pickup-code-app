package com.pickupcode.app.share

import com.pickupcode.app.data.CodeHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 只保存本次分享的反馈；旧任务与已关闭的反馈不能覆盖新分享。 */
class ShareRecognitionSession {
    data class Feedback(val id: Long, val processing: Boolean = true,
        val records: List<CodeHistory> = emptyList(), val message: String = "",
        val existingCount: Int = 0)
    private var nextId = 0L
    private val mutableFeedback = MutableStateFlow<Feedback?>(null)
    val feedback = mutableFeedback.asStateFlow()

    @Synchronized fun start(): Long {
        val id = ++nextId
        mutableFeedback.value = Feedback(id)
        return id
    }
    @Synchronized fun complete(id: Long, records: List<CodeHistory> = emptyList(),
        message: String = "", existingCount: Int = 0) {
        if (mutableFeedback.value?.id != id) return
        mutableFeedback.value = Feedback(id, false, records, message, existingCount)
    }
    @Synchronized fun dismiss(id: Long) {
        if (mutableFeedback.value?.id == id) mutableFeedback.value = null
    }
}

object SharedImageRecognition {
    val session = ShareRecognitionSession()
}
