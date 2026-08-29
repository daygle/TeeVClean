package com.teevclean.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

enum class Screen(val labelRes: Int) {
    OVERVIEW(R.string.overview),
    CLEAN(R.string.safe_cleanup),
    LARGE(R.string.large_files),
    APPS(R.string.app_review),
    HEALTH(R.string.device_health),
    SETTINGS(R.string.settings)
}

class TeeVViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TeeVRepository(application)

    var currentScreen by mutableStateOf(Screen.OVERVIEW)
    var storageSummary by mutableStateOf(StorageSummary(0, 0))
    var apps by mutableStateOf(emptyList<AppSummary>())
    var largeFiles by mutableStateOf(emptyList<FileSummary>())
    var customFolders by mutableStateOf(repository.getCustomFolders())
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
            customFolders = repository.getCustomFolders()
            isRefreshing = false
        }
    }

    fun addCustomFolder(uri: android.net.Uri) {
        viewModelScope.launch {
            repository.addCustomFolder(uri.toString())
            refreshData()
        }
    }

    fun removeCustomFolder(path: String) {
        viewModelScope.launch {
            repository.removeCustomFolder(path)
            refreshData()
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
