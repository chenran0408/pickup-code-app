package com.pickupcode.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.pickupcode.app.data.CodeHistory
import com.pickupcode.app.data.CodeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: CodeRepository) : ViewModel() {

    val activeHistory: StateFlow<List<CodeHistory>> = repo.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completedHistory: StateFlow<List<CodeHistory>> = repo.observeCompleted()
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

    fun markAsDone(item: CodeHistory, onSuccess: (CodeRepository.DoneBatch) -> Unit, onError: (String) -> Unit) =
        markGroupDone(listOf(item), onSuccess, onError)

    fun markGroupDone(items: List<CodeHistory>, onSuccess: (CodeRepository.DoneBatch) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val batch = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repo.markDoneBatch(items.map { it.id }).also { batch ->
                        batch.items.forEach { cancelNotifications(it) }
                    }
                }
                onSuccess(batch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeViewModel", "标记已取失败", e)
                onError("操作失败，请重试")
            }
        }
    }

    private suspend fun cancelNotifications(item: CodeHistory) {
        val context = com.pickupcode.app.App.instance
        val notifications = com.pickupcode.app.notification.CodeNotificationManager
        notifications.dismissRecord(context, item)
        if (repo.countActiveByCodeAndType(item.code, item.type) == 0) {
            val type = runCatching { com.pickupcode.app.extractor.CodeExtractor.CodeType.valueOf(item.type) }.getOrNull() ?: return
            notifications.cancelRemind(context, item.code, type)
            notifications.dismissByCodeAndType(context, type, item.code)
        }
    }

    fun undoDone(batch: CodeRepository.DoneBatch, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repo.restoreBatch(batch)
                    val context = com.pickupcode.app.App.instance
                    if (com.pickupcode.app.preferences.AppPreferences.isExpiryRemindEnabled(context)) {
                        batch.items.forEach { item ->
                            val current = repo.getByIdSuspend(item.id)
                            if (current?.isActive == true && current.expiryTime > 0) {
                                com.pickupcode.app.notification.CodeNotificationManager.scheduleExpiryReminder(
                                    context, current.code, com.pickupcode.app.extractor.CodeExtractor.CodeType.valueOf(current.type),
                                    current.source, current.expiryTime, historyId = current.id)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError("撤销失败，请在回收站恢复")
            }
        }
    }

    fun delete(item: CodeHistory, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) { repo.moveToTrash(item.id); cancelNotifications(item) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { onError("删除失败，请重试") }
        }
    }

    fun restoreCompleted(item: CodeHistory, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repo.restore(item.id)
                    val ctx = com.pickupcode.app.App.instance
                    if (item.expiryTime > System.currentTimeMillis() && com.pickupcode.app.preferences.AppPreferences.isExpiryRemindEnabled(ctx)) {
                        com.pickupcode.app.notification.CodeNotificationManager.scheduleExpiryReminder(ctx, item.code,
                            com.pickupcode.app.extractor.CodeExtractor.CodeType.valueOf(item.type), item.source, item.expiryTime, historyId = item.id)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { onError("恢复失败，请重试") }
        }
    }

    fun cleanExpired() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
                repo.cleanExpired(oneDayAgo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeViewModel", "回收站清理失败", e)
            }
        }
    }

    class Factory(private val repo: CodeRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(repo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }

}
