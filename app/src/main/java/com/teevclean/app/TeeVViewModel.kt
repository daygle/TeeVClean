package com.teevclean.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Builds a 24dp navigation glyph from an SVG path string.
 *
 * The path is given an explicit solid fill (the [Icon] composable recolours it via
 * its tint) so every glyph renders regardless of the builder's default brush. All
 * paths are closed, filled shapes - earlier open "stroke" paths never painted.
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
        R.string.cleanup,
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
        R.string.apps,
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
    BREAKDOWN(
        R.string.storage_breakdown,
        menuIcon(
            "breakdown",
            "M11,2v20c-5.07,-0.5,-9,-4.79,-9,-10s3.93,-9.5,9,-10zm2.03,0v8.99H22c-0.47,-4.74," +
                "-4.24,-8.52,-8.97,-8.99zm0,11.01V22c4.74,-0.47,8.5,-4.25,8.97,-8.99h-8.97z",
        ),
    ),
    HISTORY(
        R.string.history_title,
        menuIcon(
            "history",
            "M13,3c-4.97,0,-9,4.03,-9,9H1l3.89,3.89,0.07,0.14L9,12H6c0,-3.87,3.13,-7,7,-7s7,3.13,7,7," +
                "-3.13,7,-7,7c-1.93,0,-3.68,-0.79,-4.94,-2.06l-1.42,1.42C8.27,19.99,10.51,21,13,21c4.97," +
                "0,9,-4.03,9,-9s-4.03,-9,-9,-9zm-1,5v5l4.28,2.54,0.72,-1.21,-3.5,-2.08V8H12z",
        ),
    ),
    DUPLICATES(
        R.string.duplicates,
        menuIcon(
            "duplicates",
            "M16,1H4c-1.1,0,-2,0.9,-2,2v14h2V3h12V1zm3,4H8c-1.1,0,-2,0.9,-2,2v14c0,1.1,0.9,2,2,2h11c1.1," +
                "0,2,-0.9,2,-2V7c0,-1.1,-0.9,-2,-2,-2zm0,16H8V7h11v14z",
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

class TeeVViewModel(
    application: Application,
    private val repository: TeeVRepository,
) : AndroidViewModel(application) {
    /** Production constructor used by the default ViewModel factory. */
    constructor(application: Application) : this(application, TeeVRepository(application))

    var currentScreen by mutableStateOf(Screen.OVERVIEW)
    var storageSummary by mutableStateOf(StorageSummary(0, 0))
        private set
    var apps by mutableStateOf(emptyList<AppSummary>())
        private set
    var largeFiles by mutableStateOf(emptyList<FileSummary>())
        private set
    var customFolders by mutableStateOf(repository.getCustomFolders())
        private set
    var cleanupFrequency by mutableStateOf(repository.getCleanupFrequency())
        private set
    var scheduleOptions by mutableStateOf(repository.getScheduleOptions())
        private set
    /** Time of day scheduled clean-ups start, in minutes after midnight. */
    var scheduleMinuteOfDay by mutableIntStateOf(repository.getScheduleMinuteOfDay())
        private set
    var cacheSize by mutableLongStateOf(0L)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var duplicateGroups by mutableStateOf(emptyList<DuplicateGroup>())
        private set
    var isScanningDuplicates by mutableStateOf(false)
        private set
    var cleanupHistory by mutableStateOf(repository.getCleanupHistory())
        private set
    var cleanupLog by mutableStateOf(repository.getCleanupLog())
        private set
    var lastAutomaticRun by mutableLongStateOf(repository.getLastAutomaticRun())
        private set
    /** Android's estimate for the next scheduled run; null when unknown or nothing is scheduled. */
    var nextScheduledRun by mutableStateOf<Long?>(null)
        private set
    var storageBreakdown by mutableStateOf<StorageBreakdown?>(null)
        private set
    var isLoadingBreakdown by mutableStateOf(false)
        private set
    var fileFilter by mutableStateOf(repository.getFileFilter())
        private set

    private var refreshJob: Job? = null

    init {
        // Schedules from before the time-of-day option get lined up with it once.
        repository.alignLegacySchedule()
    }

    /**
     * Reloads everything shown on screen. The UI calls this on every resume (which includes
     * first launch), so a newer refresh supersedes one still in flight instead of racing it.
     */
    fun refreshData() {
        refreshJob?.cancel()
        isRefreshing = true
        refreshJob = viewModelScope.launch {
            try {
                val storageDeferred = async { repository.readStorage() }
                val appsDeferred = async { repository.loadApps() }
                val filesDeferred = async { repository.scanLargeFiles() }
                val cacheDeferred = async { repository.getCacheSize() }

                storageSummary = storageDeferred.await()
                apps = appsDeferred.await()
                largeFiles = filesDeferred.await()
                cacheSize = cacheDeferred.await()
                cleanupHistory = repository.getCleanupHistory()
                cleanupLog = repository.getCleanupLog()
                lastAutomaticRun = repository.getLastAutomaticRun()
                nextScheduledRun = repository.getNextScheduledRun()
                cleanupFrequency = repository.getCleanupFrequency()
                scheduleOptions = repository.getScheduleOptions()
                customFolders = repository.getCustomFolders()
            } finally {
                // A superseded refresh must not clear the flag while its replacement is running.
                if (refreshJob === coroutineContext[Job]) isRefreshing = false
            }
        }
    }

    fun loadBreakdown() {
        if (isLoadingBreakdown) return
        isLoadingBreakdown = true
        viewModelScope.launch {
            try {
                storageBreakdown = repository.storageBreakdown()
            } finally {
                isLoadingBreakdown = false
            }
        }
    }

    /** Shows TeeVClean's own folder browser, used where the TV has no system folder picker. */
    var folderBrowserOpen by mutableStateOf(false)

    fun addCustomFolder(uri: String) {
        repository.addCustomFolder(uri)
        invalidateDuplicates()
        refreshData()
    }

    fun removeCustomFolder(uri: String) {
        repository.removeCustomFolder(uri)
        invalidateDuplicates()
        refreshData()
    }

    /**
     * Overview's Review Clean-up: the same clean-up as the schedule (TeeVClean's cache plus
     * [options]), logged as manual. If a part fails, the parts that finished are still recorded.
     */
    fun cleanNow(options: ScheduleOptions, onComplete: (CleanupResult) -> Unit) {
        viewModelScope.launch {
            val kinds = mutableListOf<CleanupKind>()
            val results = mutableListOf<CleanupResult>()
            val finished = try {
                repository.runCleanup(options, kinds, results)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            val result = CleanupResult(results.sumOf { it.freedBytes }, results.sumOf { it.itemsRemoved })
            repository.recordCleanup(kinds, result, automatic = false, complete = finished)
            invalidateDuplicates()
            refreshData()
            onComplete(result)
        }
    }

    fun deleteFiles(files: List<FileSummary>, onComplete: (CleanupResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteFiles(files)
            record(CleanupKind.FILES, result)
            invalidateDuplicates()
            refreshData()
            onComplete(result)
        }
    }

    fun findDuplicates() {
        if (isScanningDuplicates) return
        isScanningDuplicates = true
        viewModelScope.launch {
            try {
                duplicateGroups = repository.scanDuplicates()
            } finally {
                isScanningDuplicates = false
            }
        }
    }

    fun deleteDuplicates(files: List<FileSummary>, onComplete: (CleanupResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteFiles(files)
            record(CleanupKind.DUPLICATES, result)
            invalidateDuplicates()
            findDuplicates()
            refreshData()
            onComplete(result)
        }
    }

    /** Logs a clean-up the user started. Preferences write in memory and persist asynchronously. */
    private fun record(kind: CleanupKind, result: CleanupResult) {
        repository.recordCleanup(listOf(kind), result, automatic = false)
    }

    fun clearCleanupLog() {
        repository.clearCleanupLog()
        cleanupLog = emptyList()
    }

    /**
     * Drops duplicate groups that may no longer match the disk. A stale group is dangerous:
     * if its kept copy was deleted elsewhere, "delete extras" would remove the last copy.
     */
    private fun invalidateDuplicates() {
        duplicateGroups = emptyList()
    }

    fun uninstallApp(packageName: String) = repository.uninstallApp(packageName)

    fun sweepJunk(onComplete: (CleanupResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.sweepJunk()
            record(CleanupKind.SWEEP_FOLDERS, result)
            invalidateDuplicates()
            refreshData()
            onComplete(result)
        }
    }

    fun openStorageManager() = repository.openStorageManager()

    fun freeBytes(): Long = repository.freeBytes()

    /** Records what Android's clear-all-caches prompt freed, measured against [freeBefore]. */
    fun onSystemCachesCleared(freeBefore: Long, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                // Android doesn't report an item count, so only the bytes are recorded.
                (repository.freeBytes() - freeBefore).coerceAtLeast(0L).also {
                    repository.recordCleanup(listOf(CleanupKind.APP_CACHES), CleanupResult(it, 0), automatic = false)
                }
            }
            refreshData()
            onComplete(freed)
        }
    }

    fun setSchedule(frequency: CleanupFrequency, options: ScheduleOptions, minuteOfDay: Int) {
        repository.setCleanupSchedule(frequency, options, minuteOfDay)
        cleanupFrequency = repository.getCleanupFrequency()
        scheduleOptions = repository.getScheduleOptions()
        scheduleMinuteOfDay = repository.getScheduleMinuteOfDay()
        // WorkManager plans the new run in the background, so wait briefly for its time.
        if (frequency == CleanupFrequency.OFF) {
            nextScheduledRun = null
        } else {
            viewModelScope.launch { nextScheduledRun = repository.getNextScheduledRun(waitMillis = 3_000L) }
        }
    }

    fun openAppInfo(packageName: String) = repository.openAppInfo(packageName)

    fun hasFullStorageAccess(): Boolean = repository.hasFullStorageAccess()

    fun updateFileFilter(filter: FileFilter) {
        repository.setFileFilter(filter)
        fileFilter = filter
        refreshData()
    }
}
