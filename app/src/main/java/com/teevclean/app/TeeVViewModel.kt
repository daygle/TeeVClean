package com.teevclean.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Builds a 24dp navigation glyph from an SVG path string.
 *
 * The path is given an explicit solid fill (the [Icon] composable recolours it via
 * its tint) so every glyph renders regardless of the builder's default brush. All
 * paths are closed, filled shapes — earlier open "stroke" paths never painted.
 */
private fun menuIcon(
    name: String,
    pathData: String,
    fillType: PathFillType = PathFillType.NonZero,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString(pathData).toNodes(),
    pathFillType = fillType,
    fill = SolidColor(Color.Black),
).build()

enum class Screen(val labelRes: Int, val icon: ImageVector) {
    OVERVIEW(
        R.string.overview,
        menuIcon("overview", "M3,13h8V3H3v10zm0,8h8v-6H3v6zm10,0h8V11h-8v10zm0,-18v6h8V3h-8z"),
    ),
    CLEAN(
        R.string.safe_cleanup,
        menuIcon(
            "clean",
            "M16,11h-1V3c0,-1.1,-0.9,-2,-2,-2h-2C9.9,1,9,1.9,9,3v8H8c-1.66,0,-3,1.34,-3,3v7h1v1c0," +
                "0.55,0.45,1,1,1s1,-0.45,1,-1v-1h8v1c0,0.55,0.45,1,1,1s1,-0.45,1,-1v-1h1v-7C19," +
                "12.34,17.66,11,16,11z",
        ),
    ),
    LARGE(
        R.string.large_files,
        menuIcon(
            "files",
            "M10,4H4c-1.1,0,-1.99,0.9,-1.99,2L2,18c0,1.1,0.9,2,2,2h16c1.1,0,2,-0.9,2,-2V8c0," +
                "-1.1,-0.9,-2,-2,-2h-8l-2,-2z",
        ),
    ),
    APPS(
        R.string.app_review,
        menuIcon(
            "apps",
            "M4,8h4V4H4v4zm6,12h4v-4h-4v4zm-6,0h4v-4H4v4zm0,-6h4v-4H4v4zm6,0h4v-4h-4v4zm6,-10v4h4V4" +
                "h-4zm-6,4h4V4h-4v4zm6,6h4v-4h-4v4zm0,6h4v-4h-4v4z",
        ),
    ),
    HEALTH(
        R.string.device_health,
        menuIcon(
            "health",
            "M12,21.35l-1.45,-1.32C5.4,15.36,2,12.28,2,8.5,2,5.42,4.42,3,7.5,3c1.74,0,3.41,0.81," +
                "4.5,2.09C13.09,3.81,14.76,3,16.5,3,19.58,3,22,5.42,22,8.5c0,3.78,-3.4,6.86," +
                "-8.55,11.54L12,21.35z",
        ),
    ),
    SETTINGS(
        R.string.settings,
        menuIcon(
            "settings",
            "M19.14,12.94c0.04,-0.3,0.06,-0.61,0.06,-0.94c0,-0.32,-0.02,-0.64,-0.07,-0.94l2.03," +
                "-1.58c0.18,-0.14,0.23,-0.41,0.12,-0.61l-1.92,-3.32c-0.12,-0.22,-0.37,-0.29," +
                "-0.59,-0.22l-2.39,0.96c-0.5,-0.38,-1.03,-0.7,-1.62,-0.94L14.4,2.81c-0.04,-0.24," +
                "-0.24,-0.41,-0.48,-0.41h-3.84c-0.24,0,-0.43,0.17,-0.47,0.41L9.25,5.35C8.66,5.59," +
                "8.12,5.92,7.63,6.29L5.24,5.33c-0.22,-0.08,-0.47,0,-0.59,0.22L2.74,8.87C2.62," +
                "9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.07," +
                "0.94l-2.03,1.58c-0.18,0.14,-0.23,0.41,-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29," +
                "0.59,0.22l2.39,-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54c0.05,0.24,0.24,0.41," +
                "0.48,0.41h3.84c0.24,0,0.44,-0.17,0.47,-0.41l0.36,-2.54c0.59,-0.24,1.13,-0.56," +
                "1.62,-0.94l2.39,0.96c0.22,0.08,0.47,0,0.59,-0.22l1.92,-3.32c0.12,-0.22,0.07," +
                "-0.47,-0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0,-3.6,-1.62,-3.6,-3.6s1.62,-3.6," +
                "3.6,-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z",
            PathFillType.EvenOdd,
        ),
    ),
}

class TeeVViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TeeVRepository(application)

    var currentScreen by mutableStateOf(Screen.OVERVIEW)
    var storageSummary by mutableStateOf(StorageSummary(0, 0))
    var apps by mutableStateOf(emptyList<AppSummary>())
    var largeFiles by mutableStateOf(emptyList<FileSummary>())
    var customFolders by mutableStateOf(repository.getCustomFolders())
    var cleanupFrequency by mutableStateOf(repository.getCleanupFrequency())
    var scheduleSweepEnabled by mutableStateOf(repository.isScheduledSweepEnabled())
    var cacheSize by mutableLongStateOf(0L)
    var isRefreshing by mutableStateOf(false)
    var lastCleanupResult by mutableStateOf<CleanupResult?>(null)

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
            cleanupFrequency = repository.getCleanupFrequency()
            scheduleSweepEnabled = repository.isScheduledSweepEnabled()
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

    fun clearCache(onComplete: (CleanupResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.clearOwnCache()
            lastCleanupResult = result
            refreshData()
            onComplete(result)
        }
    }

    fun deleteLargeFile(uri: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteFile(uri)
            refreshData()
            onComplete()
        }
    }

    fun sweepJunk(onComplete: (CleanupResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.sweepJunk()
            lastCleanupResult = result
            refreshData()
            onComplete(result)
        }
    }

    fun openStorageManager() = repository.openStorageManager()

    fun setSchedule(frequency: CleanupFrequency, includeSweep: Boolean) {
        repository.setCleanupSchedule(frequency, includeSweep)
        cleanupFrequency = repository.getCleanupFrequency()
        scheduleSweepEnabled = repository.isScheduledSweepEnabled()
    }

    fun openAppInfo(packageName: String) {
        repository.openAppInfo(packageName)
    }
}
