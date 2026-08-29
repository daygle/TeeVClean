package com.teevclean.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

enum class Screen(val labelRes: Int, val icon: ImageVector) {
    OVERVIEW(R.string.overview, Icons.Outlined.Dashboard),
    CLEAN(R.string.safe_cleanup, Icons.Outlined.CleaningServices),
    LARGE(R.string.large_files, Icons.Outlined.FolderOpen),
    APPS(R.string.app_review, Icons.Outlined.Apps),
    HEALTH(R.string.device_health, Icons.Outlined.MonitorHeart),
    SETTINGS(R.string.settings, Icons.Outlined.Settings)
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
