package com.pickupcode.app.ui.screens.home

import android.util.Log
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.pickupcode.app.data.CodeHistory
import com.pickupcode.app.data.CodeRepository
import com.pickupcode.app.service.CodeCompletion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: CodeRepository, private val context: Context) : ViewModel() {

    val activeHistory: StateFlow<List<CodeHistory>> = repo.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trashHistory: StateFlow<List<CodeHistory>> = repo.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var dedupCount by mutableIntStateOf(0)
        private set

    fun refreshDedupCount() {
        viewModelScope.launch(Dispatchers.IO) {
            dedupCount = repo.countDuplicateGroups()
        }
    }

    fun markAsDone(item: CodeHistory, onSuccess: (Long) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doneAt = CodeCompletion.markDone(context, repo, item.id)
                if (doneAt != null) onSuccess(doneAt)
                else onError("记录已不在待取列表")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeViewModel", "标记已取失败", e)
                onError("操作失败，请重试")
            }
        }
    }

    fun undoDone(item: CodeHistory, doneAt: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.getArchivedByAction(item.code, item.type, doneAt)
                    .forEach { CodeCompletion.restore(context, repo, it.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeViewModel", "撤销归档失败", e)
            }
        }
    }

    fun cleanExpired() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
                repo.cleanExpired(oneDayAgo) { path ->
                    try { java.io.File(path).delete() } catch (e: Exception) { Log.w("HomeViewModel", "截图清理失败: $path", e) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeViewModel", "回收站清理失败", e)
            }
        }
    }

    class Factory(private val repo: CodeRepository, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(repo, context.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }

}
