package com.teevclean.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
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
data class FileSummary(val name: String, val path: String, val size: Long, val modified: Long)

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

    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        iterativeFolderSize(context.cacheDir)
    }

    suspend fun clearOwnCache() = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    suspend fun scanLargeFiles(): List<FileSummary> = withContext(Dispatchers.IO) {
        val publicRoots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        )
        val customFolders = getCustomFolders().mapNotNull { uriString ->
            try {
                val uri = uriString.toUri()
                // Note: For TV, we might need to handle Uri-to-File conversion or use DocumentFile
                // For simplicity in this refactor, we assume direct File access if possible, 
                // but real SAF implementation would use DocumentFile.
                File(uri.path ?: "") 
            } catch (_: Exception) { null }
        }

        val roots = publicRoots + customFolders
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val largeFileBytes = 500L * 1024 * 1024

        roots.flatMap { root ->
            if (root.exists() && root.isDirectory) {
                root.listFiles()?.filter { it.isFile && (it.length() >= largeFileBytes || it.lastModified() < cutoff) }
                    ?.map { FileSummary(it.name, it.parent.orEmpty(), it.length(), it.lastModified()) }
                    .orEmpty()
            } else emptyList()
        }.sortedByDescending { it.size }
    }

    suspend fun loadApps(): List<AppSummary> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val since = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000
        val lastUsed = usage?.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, since, System.currentTimeMillis())
            ?.associate { it.packageName to it.lastTimeUsed }.orEmpty()

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
    }
}
