package com.teevclean.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

private fun menuIcon(name: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).path {
    when (name) {
        "overview" -> { moveTo(3f, 3f); horizontalLineTo(10f); verticalLineTo(10f); horizontalLineTo(3f); close(); moveTo(14f, 3f); horizontalLineTo(21f); verticalLineTo(10f); horizontalLineTo(14f); close(); moveTo(3f, 14f); horizontalLineTo(10f); verticalLineTo(21f); horizontalLineTo(3f); close(); moveTo(14f, 14f); horizontalLineTo(21f); verticalLineTo(21f); horizontalLineTo(14f); close() }
        "clean" -> { moveTo(9f, 3f); lineTo(11f, 3f); lineTo(11f, 12f); lineTo(9f, 12f); close(); moveTo(13f, 3f); lineTo(15f, 3f); lineTo(15f, 12f); lineTo(13f, 12f); close(); moveTo(7f, 10f); lineTo(17f, 10f); lineTo(16f, 21f); lineTo(8f, 21f); close() }
        "files" -> { moveTo(3f, 5f); lineTo(9f, 5f); lineTo(11f, 7f); lineTo(21f, 7f); lineTo(21f, 19f); lineTo(3f, 19f); close() }
        "apps" -> { moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close(); moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close(); moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close(); moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close() }
        "health" -> { moveTo(3f, 12f); horizontalLineTo(7f); lineTo(9f, 5f); lineTo(13f, 19f); lineTo(15f, 12f); horizontalLineTo(21f) }
        "settings" -> { moveTo(12f, 8f); arcTo(4f, 4f, 0f, false, true, 0f, 8f); arcTo(4f, 4f, 0f, false, true, 0f, -8f); moveTo(12f, 3f); verticalLineTo(1f); moveTo(12f, 23f); verticalLineTo(21f); moveTo(3f, 12f); horizontalLineTo(1f); moveTo(23f, 12f); horizontalLineTo(21f) }
    }
}.build()

enum class Screen(val labelRes: Int, val icon: ImageVector) {
    OVERVIEW(R.string.overview, menuIcon("overview")),
    CLEAN(R.string.safe_cleanup, menuIcon("clean")),
    LARGE(R.string.large_files, menuIcon("files")),
    APPS(R.string.app_review, menuIcon("apps")),
    HEALTH(R.string.device_health, menuIcon("health")),
    SETTINGS(R.string.settings, menuIcon("settings"))
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
