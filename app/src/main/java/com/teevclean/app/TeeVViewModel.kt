package com.teevclean.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

enum class Screen(val label: String) {
    OVERVIEW("Overview"),
    CLEAN("Safe Cleanup"),
    LARGE("Large Files"),
    APPS("App Review"),
    HEALTH("Device Health")
}

class TeeVViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TeeVRepository(application)

    var currentScreen by mutableStateOf(Screen.OVERVIEW)
    var storageSummary by mutableStateOf(StorageSummary(0, 0))
    var apps by mutableStateOf(emptyList<AppSummary>())
    var largeFiles by mutableStateOf(emptyList<FileSummary>())
    var scheduleEnabled by mutableStateOf(repository.isCleanupScheduled())
    var cacheSize by mutableLongStateOf(0L)
    var isRefreshing by mutableStateOf(false)

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            isRefreshing = true
            storageSummary = repository.readStorage()
            apps = repository.loadApps()
            largeFiles = repository.scanLargeFiles()
            cacheSize = repository.getCacheSize()
            scheduleEnabled = repository.isCleanupScheduled()
            isRefreshing = false
        }
    }

    fun clearCache(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearOwnCache()
            refreshData()
            onComplete()
        }
    }

    fun toggleSchedule(enabled: Boolean) {
        repository.setCleanupSchedule(enabled)
        scheduleEnabled = repository.isCleanupScheduled()
    }

    fun openAppInfo(packageName: String) {
        repository.openAppInfo(packageName)
    }
}
