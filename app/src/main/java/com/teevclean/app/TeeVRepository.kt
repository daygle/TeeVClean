package com.teevclean.app

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

data class StorageSummary(val used: Long, val total: Long) {
    val free: Long get() = (total - used).coerceAtLeast(0)
    val fraction: Float get() = if (total == 0L) 0f else (used.toFloat() / total).coerceIn(0f, 1f)
}

data class AppSummary(val label: String, val packageName: String, val size: Long, val lastUsed: Long)

/**
 * A large or old file discovered during a scan.
 *
 * [uri] is the handle used to remove the file: a `content://` document Uri for entries
 * found through the Storage Access Framework (always deletable), or a `file://` Uri for
 * entries read directly from public storage (best-effort delete).
 */
data class FileSummary(
    val name: String,
    val path: String,
    val size: Long,
    val modified: Long,
    val uri: String,
)

/** Outcome of a cleanup action, so the UI can report what was actually removed. */
data class CleanupResult(val freedBytes: Long, val itemsRemoved: Int)

/**
 * A set of files that appear to be identical. [files] is ordered newest-first; keeping the
 * first and removing the rest reclaims [reclaimableBytes].
 */
data class DuplicateGroup(val files: List<FileSummary>, val sizeEach: Long) {
    val reclaimableBytes: Long get() = sizeEach * (files.size - 1).coerceAtLeast(0)
}

/** How often the unattended background cleanup runs. [intervalDays] is null when disabled. */
enum class CleanupFrequency(val intervalDays: Long?) {
    OFF(null),
    DAILY(1),
    WEEKLY(7),
    MONTHLY(30),
}

/** A device-storage breakdown. [systemAndOtherBytes] is what used space isn't attributed to user apps. */
data class StorageBreakdown(
    val totalBytes: Long,
    val freeBytes: Long,
    val appsBytes: Long,
    val appCacheBytes: Long,
    val appDataKnown: Boolean,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val systemAndOtherBytes: Long get() = (usedBytes - appsBytes).coerceAtLeast(0)
}

/** Running totals for the cleanup-history card. */
data class CleanupHistory(val lastRun: Long, val totalFreedBytes: Long, val totalItems: Int)

/** Pure filename rules for cleanup, extracted so they can be unit-tested without Android. */
object FileClassifier {
    /** Junk that is always safe to remove: temp/log/thumbnail files and abandoned partial downloads. */
    fun isJunk(name: String?): Boolean {
        val lower = name?.lowercase() ?: return false
        return lower.endsWith(".tmp") || lower.endsWith(".temp") || lower.endsWith(".log") ||
            lower == ".ds_store" || lower == "thumbs.db" || lower.endsWith(".thumbnails") ||
            lower.endsWith(".part") || lower.endsWith(".partial") ||
            lower.endsWith(".crdownload") || lower.endsWith(".download")
    }

    /** Leftover app installers, worth reviewing for removal once the app is installed. */
    fun isInstaller(name: String?): Boolean {
        val lower = name?.lowercase() ?: return false
        return lower.endsWith(".apk") || lower.endsWith(".xapk") ||
            lower.endsWith(".apkm") || lower.endsWith(".obb")
    }
}

class TeeVRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("teevclean_prefs", Context.MODE_PRIVATE)

    fun getCustomFolders(): List<String> =
        prefs.getStringSet("custom_folders", emptySet())?.toList() ?: emptyList()

    fun addCustomFolder(uri: String) {
        val folders = getCustomFolders().toMutableSet()
        folders.add(uri)
        prefs.edit { putStringSet("custom_folders", folders) }
    }

    fun removeCustomFolder(uri: String) {
        val folders = getCustomFolders().toMutableSet()
        folders.remove(uri)
        prefs.edit { putStringSet("custom_folders", folders) }
    }

    suspend fun readStorage(): StorageSummary = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        StorageSummary((total - stat.availableBytes).coerceIn(0L, total), total)
    }

    /**
     * Breaks device storage into user-app usage (with its cache subtotal) and everything else.
     * App attribution needs usage access + API 26; without it [StorageBreakdown.appDataKnown] is
     * false and only total/free are meaningful.
     */
    suspend fun storageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBytes
        var appsBytes = 0L
        var cacheBytes = 0L
        var known = false
        val statsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(StorageStatsManager::class.java)
        } else {
            null
        }
        if (statsManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (app in context.packageManager.getInstalledApplications(0)) {
                if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                try {
                    val stats = statsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, Process.myUserHandle())
                    appsBytes += stats.appBytes + stats.dataBytes + stats.cacheBytes
                    cacheBytes += stats.cacheBytes
                    known = true
                } catch (_: Exception) {
                    // No usage access for this package; leave it out of the app total.
                }
            }
        }
        StorageBreakdown(total, free, appsBytes, cacheBytes, known)
    }

    fun getCleanupHistory(): CleanupHistory = CleanupHistory(
        prefs.getLong(KEY_LAST_RUN, 0L),
        prefs.getLong(KEY_TOTAL_FREED, 0L),
        prefs.getInt(KEY_TOTAL_ITEMS, 0),
    )

    private fun recordCleanup(result: CleanupResult) {
        prefs.edit {
            putLong(KEY_LAST_RUN, System.currentTimeMillis())
            putLong(KEY_TOTAL_FREED, prefs.getLong(KEY_TOTAL_FREED, 0L) + result.freedBytes)
            putInt(KEY_TOTAL_ITEMS, prefs.getInt(KEY_TOTAL_ITEMS, 0) + result.itemsRemoved)
        }
    }

    /** All of TeeVClean's own cache locations. externalCacheDir can be null when no media is mounted. */
    private fun ownCacheDirs(): List<File> =
        listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir)

    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        ownCacheDirs().sumOf { iterativeFolderSize(it) }
    }

    suspend fun clearOwnCache(): CleanupResult = withContext(Dispatchers.IO) {
        var freed = 0L
        var removed = 0
        ownCacheDirs().forEach { dir ->
            dir.listFiles()?.forEach { entry ->
                val entrySize = iterativeFolderSize(entry)
                if (entry.deleteRecursively()) {
                    freed += entrySize
                    removed++
                }
            }
        }
        CleanupResult(freed, removed).also { recordCleanup(it) }
    }

    suspend fun scanLargeFiles(): List<FileSummary> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val largeFileBytes = 500L * 1024 * 1024

        // Public storage roots read directly (works where the app has legacy/all-files access).
        val publicRoots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        )
        val publicMatches = publicRoots.flatMap { root ->
            if (root.exists() && root.isDirectory) {
                root.listFiles()
                    ?.filter { it.isFile && (it.length() >= largeFileBytes || it.lastModified() < cutoff || isInstaller(it.name)) }
                    ?.map { FileSummary(it.name, it.parent.orEmpty(), it.length(), it.lastModified(), Uri.fromFile(it).toString()) }
                    .orEmpty()
            } else emptyList()
        }

        // Folders the user granted through the Storage Access Framework; entries here are deletable.
        val safMatches = getCustomFolders().flatMap { uriString ->
            walkSafTree(uriString) { doc ->
                doc.length() >= largeFileBytes || doc.lastModified() < cutoff || isInstaller(doc.name)
            }
        }

        (publicMatches + safMatches).sortedByDescending { it.size }
    }

    /**
     * Bounded breadth-first walk over a SAF tree, returning files that match [predicate].
     * The node cap keeps a pathological folder tree from stalling the scan.
     */
    private fun walkSafTree(treeUriString: String, predicate: (DocumentFile) -> Boolean): List<FileSummary> {
        val root = try {
            DocumentFile.fromTreeUri(context, treeUriString.toUri())
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val matches = mutableListOf<FileSummary>()
        val queue = ArrayDeque<DocumentFile>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_SCAN_NODES) {
            val dir = queue.removeFirst()
            for (child in dir.listFiles()) {
                visited++
                if (child.isDirectory) {
                    queue.add(child)
                } else if (child.isFile && predicate(child)) {
                    matches.add(
                        FileSummary(
                            name = child.name ?: "unknown",
                            path = dir.name ?: root.name.orEmpty(),
                            size = child.length(),
                            modified = child.lastModified(),
                            uri = child.uri.toString(),
                        )
                    )
                }
                if (visited >= MAX_SCAN_NODES) break
            }
        }
        return matches
    }

    /** Deletes a single scanned file by its handle. Content Uris use SAF; file Uris use direct IO. */
    suspend fun deleteFile(uriString: String): Boolean = withContext(Dispatchers.IO) {
        val uri = try {
            uriString.toUri()
        } catch (_: Exception) {
            return@withContext false
        }
        try {
            when (uri.scheme) {
                "content" -> DocumentFile.fromSingleUri(context, uri)?.delete() == true
                "file" -> uri.path?.let { File(it).delete() } == true
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Deletes several scanned files, reporting how much was freed. Sizes are captured before deletion. */
    suspend fun deleteFiles(files: List<FileSummary>): CleanupResult = withContext(Dispatchers.IO) {
        var freed = 0L
        var removed = 0
        for (file in files) {
            if (deleteFile(file.uri)) {
                freed += file.size
                removed++
            }
        }
        CleanupResult(freed, removed).also { recordCleanup(it) }
    }

    /**
     * Finds groups of files that look identical, within the folders the user granted.
     * Files are grouped by exact size and then by a content fingerprint (SHA-256 of the
     * first [FINGERPRINT_BYTES]), which is fast and a strong signal for real-world media
     * and documents. Deletion is always user-confirmed, so the fingerprint heuristic is safe.
     */
    suspend fun scanDuplicates(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val candidates = getCustomFolders().flatMap { uriString ->
            walkSafTree(uriString) { doc -> doc.length() >= MIN_DUPLICATE_BYTES }
        }
        candidates
            .groupBy { it.size }
            .filterValues { it.size > 1 }
            .flatMap { (size, sameSize) ->
                sameSize
                    .groupBy { fileFingerprint(it.uri) }
                    .filterKeys { it != null }
                    .values
                    .filter { it.size > 1 }
                    .map { group -> DuplicateGroup(group.sortedByDescending { it.modified }, size) }
            }
            .sortedByDescending { it.reclaimableBytes }
    }

    private fun fileFingerprint(uriString: String): String? = try {
        context.contentResolver.openInputStream(uriString.toUri())?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (total < FINGERPRINT_BYTES) {
                val read = input.read(buffer, 0, minOf(buffer.size, FINGERPRINT_BYTES - total))
                if (read <= 0) break
                digest.update(buffer, 0, read)
                total += read
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (_: Exception) {
        null
    }

    /** Removes obvious junk (temp/log/thumbnail files and empty folders) from granted SAF folders. */
    suspend fun sweepJunk(): CleanupResult = withContext(Dispatchers.IO) {
        var freed = 0L
        var removed = 0
        getCustomFolders().forEach { treeUriString ->
            val root = try {
                DocumentFile.fromTreeUri(context, treeUriString.toUri())
            } catch (_: Exception) {
                null
            } ?: return@forEach

            // Deepest-first so a folder is only empty-checked after its children are handled.
            val ordered = collectSafNodes(root)
            for (doc in ordered.asReversed()) {
                if (doc.isFile && isJunk(doc.name)) {
                    val size = doc.length()
                    if (doc.delete()) { freed += size; removed++ }
                } else if (doc.isDirectory && doc.uri != root.uri && doc.listFiles().isEmpty()) {
                    if (doc.delete()) removed++
                }
            }
        }
        CleanupResult(freed, removed).also { recordCleanup(it) }
    }

    private fun collectSafNodes(root: DocumentFile): List<DocumentFile> {
        val nodes = mutableListOf<DocumentFile>()
        val queue = ArrayDeque<DocumentFile>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_SCAN_NODES) {
            val dir = queue.removeFirst()
            for (child in dir.listFiles()) {
                visited++
                nodes.add(child)
                if (child.isDirectory) queue.add(child)
                if (visited >= MAX_SCAN_NODES) break
            }
        }
        return nodes
    }

    private fun isJunk(name: String?): Boolean = FileClassifier.isJunk(name)

    private fun isInstaller(name: String?): Boolean = FileClassifier.isInstaller(name)

    /** Opens the system "free up space" storage manager so the user can act device-wide. */
    fun openStorageManager() {
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Intent(StorageManager.ACTION_MANAGE_STORAGE)
        } else {
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(primary)
        } catch (_: Exception) {
            try {
                context.startActivity(fallback)
            } catch (_: Exception) {
                // No storage settings activity available on this device.
            }
        }
    }

    @RequiresPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    private fun readLastUsedByPackage(usage: UsageStatsManager?, since: Long): Map<String, Long> =
        usage?.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, since, System.currentTimeMillis())
            ?.associate { it.packageName to it.lastTimeUsed }
            .orEmpty()

    suspend fun loadApps(): List<AppSummary> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val since = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000
        val lastUsed = try {
            readLastUsedByPackage(usage, since)
        } catch (_: SecurityException) {
            emptyMap()
        }

        val statsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(StorageStatsManager::class.java)
        } else {
            null
        }

        pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }
            .map { app ->
                val apkBytes = File(app.sourceDir ?: "").length()
                AppSummary(
                    app.loadLabel(pm).toString(),
                    app.packageName,
                    appStorageBytes(statsManager, app.packageName, apkBytes),
                    lastUsed[app.packageName] ?: 0L
                )
            }.sortedByDescending { it.size }
    }

    /**
     * Real footprint of an app: installed code + data + cache, via StorageStatsManager
     * (needs usage access, API 26+). Falls back to the APK size when unavailable, so the
     * value is never worse than the old estimate.
     */
    private fun appStorageBytes(statsManager: StorageStatsManager?, packageName: String, fallbackApk: Long): Long {
        if (statsManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return fallbackApk
        return try {
            val stats = statsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, packageName, Process.myUserHandle())
            stats.appBytes + stats.dataBytes + stats.cacheBytes
        } catch (_: Exception) {
            fallbackApk
        }
    }

    fun getCleanupFrequency(): CleanupFrequency =
        runCatching {
            CleanupFrequency.valueOf(prefs.getString(KEY_FREQUENCY, CleanupFrequency.OFF.name)!!)
        }.getOrDefault(CleanupFrequency.OFF)

    fun isScheduledSweepEnabled(): Boolean = prefs.getBoolean(KEY_INCLUDE_SWEEP, false)

    /**
     * Configures the recurring background cleanup. Only unattended-safe actions are ever
     * scheduled: the app's own cache always, and — when [includeSweep] is set — the temp/log
     * sweep of folders the user granted. Other apps' caches and user-file deletion are never
     * automated because they need explicit interaction.
     */
    fun setCleanupSchedule(frequency: CleanupFrequency, includeSweep: Boolean) {
        prefs.edit {
            putString(KEY_FREQUENCY, frequency.name)
            putBoolean(KEY_INCLUDE_SWEEP, includeSweep)
        }
        val manager = WorkManager.getInstance(context)
        val intervalDays = frequency.intervalDays
        if (intervalDays == null) {
            manager.cancelUniqueWork(WEEKLY_CLEANUP_WORK)
        } else {
            val request = PeriodicWorkRequestBuilder<CleanupWorker>(intervalDays, TimeUnit.DAYS)
                .setInputData(workDataOf(INCLUDE_SWEEP to includeSweep))
                .build()
            manager.enqueueUniquePeriodicWork(
                WEEKLY_CLEANUP_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Launches the system uninstall prompt for a package. The user confirms the removal. */
    fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, "package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppInfo(packageName)
        }
    }

    private fun iterativeFolderSize(root: File): Long {
        if (!root.exists()) return 0L
        if (root.isFile) return root.length()

        var totalSize = 0L
        val stack = ArrayDeque<File>()
        stack.push(root)

        while (stack.isNotEmpty()) {
            val current = stack.pop()
            val files = current.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile) {
                        totalSize += file.length()
                    } else if (file.isDirectory) {
                        stack.push(file)
                    }
                }
            }
        }
        return totalSize
    }

    companion object {
        const val WEEKLY_CLEANUP_WORK = "weekly-cache-cleanup"
        const val INCLUDE_SWEEP = "include_sweep"
        private const val MAX_SCAN_NODES = 5000
        private const val KEY_FREQUENCY = "cleanup_frequency"
        private const val KEY_INCLUDE_SWEEP = "cleanup_include_sweep"
        private const val KEY_LAST_RUN = "cleanup_last_run"
        private const val KEY_TOTAL_FREED = "cleanup_total_freed"
        private const val KEY_TOTAL_ITEMS = "cleanup_total_items"

        /** Ignore files below this size when hunting duplicates — tiny files aren't worth the churn. */
        private const val MIN_DUPLICATE_BYTES = 1L * 1024 * 1024

        /** Bytes hashed per file to fingerprint content for duplicate detection. */
        private const val FINGERPRINT_BYTES = 1 * 1024 * 1024
    }
}
