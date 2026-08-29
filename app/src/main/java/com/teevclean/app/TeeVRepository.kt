package com.teevclean.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
        CleanupResult(freed, removed)
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
                    ?.filter { it.isFile && (it.length() >= largeFileBytes || it.lastModified() < cutoff) }
                    ?.map { FileSummary(it.name, it.parent.orEmpty(), it.length(), it.lastModified(), Uri.fromFile(it).toString()) }
                    .orEmpty()
            } else emptyList()
        }

        // Folders the user granted through the Storage Access Framework; entries here are deletable.
        val safMatches = getCustomFolders().flatMap { uriString ->
            walkSafTree(uriString) { doc ->
                doc.length() >= largeFileBytes || doc.lastModified() < cutoff
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
        CleanupResult(freed, removed)
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

    private fun isJunk(name: String?): Boolean {
        val lower = name?.lowercase() ?: return false
        return lower.endsWith(".tmp") || lower.endsWith(".temp") || lower.endsWith(".log") ||
            lower == ".ds_store" || lower == "thumbs.db" || lower.endsWith(".thumbnails")
    }

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

        pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }
            .map { app ->
                AppSummary(
                    app.loadLabel(pm).toString(),
                    app.packageName,
                    File(app.sourceDir ?: "").length(),
                    lastUsed[app.packageName] ?: 0L
                )
            }.sortedByDescending { it.size }
    }

    fun isCleanupScheduled(): Boolean = try {
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(WEEKLY_CLEANUP_WORK).get()
            .any { it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.RUNNING }
    } catch (_: Exception) {
        false
    }

    fun setCleanupSchedule(enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WEEKLY_CLEANUP_WORK)
        } else {
            manager.enqueueUniquePeriodicWork(
                WEEKLY_CLEANUP_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CleanupWorker>(7, TimeUnit.DAYS).build(),
            )
        }
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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
        private const val MAX_SCAN_NODES = 5000
    }
}
