package com.teevclean.app

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
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
    /** An installer whose app is already installed at the same or a newer version. */
    val alreadyInstalled: Boolean = false,
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

/** Which files the Files screen lists: anything large, old, or (optionally) a leftover installer. */
data class FileFilter(
    val minSize: Long = 500L * 1024 * 1024,
    val minAgeDays: Int = 30,
    val showInstallers: Boolean = true,
) {
    /** Files last modified before this instant count as old. */
    fun ageCutoff(now: Long): Long = now - TimeUnit.DAYS.toMillis(minAgeDays.toLong())

    fun isLarge(size: Long): Boolean = size >= minSize

    fun isOld(modified: Long, now: Long): Boolean = modified < ageCutoff(now)

    fun isListedInstaller(name: String?): Boolean = showInstallers && FileClassifier.isInstaller(name)

    fun matches(name: String?, size: Long, modified: Long, now: Long): Boolean =
        isLarge(size) || isOld(modified, now) || isListedInstaller(name)
}

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

    /**
     * Metadata other computers leave on shared drives: macOS AppleDouble (`._name`) companions
     * and Windows folder settings. Android never reads them.
     */
    fun isForeignMetadata(name: String?): Boolean {
        val lower = name?.lowercase() ?: return false
        return lower.startsWith("._") || lower == "desktop.ini"
    }

    /**
     * Folders that are safe to remove whole: index, trash and thumbnail caches that the OS
     * that made them rebuilds on demand (Android regenerates `.thumbnails` itself).
     */
    fun isJunkFolder(name: String?): Boolean {
        val lower = name?.lowercase() ?: return false
        return lower == ".thumbnails" || lower == ".spotlight-v100" || lower == ".trashes" ||
            lower == ".fseventsd" || lower.startsWith(".trash-")
    }

    /**
     * Unfinished torrent pieces (qBittorrent `.!qB`, uTorrent `.!ut`). Only counted once
     * untouched for [STALE_INCOMPLETE_DAYS], so a download that is still running is left alone.
     * An unknown date (0) never counts as stale.
     */
    fun isStaleIncompleteDownload(name: String?, modified: Long, now: Long): Boolean {
        val lower = name?.lowercase() ?: return false
        if (!lower.endsWith(".!qb") && !lower.endsWith(".!ut")) return false
        return modified > 0 && modified < now - TimeUnit.DAYS.toMillis(STALE_INCOMPLETE_DAYS)
    }

    /** Every file rule the temp-file sweep applies. */
    fun isSweepableFile(name: String?, modified: Long, now: Long): Boolean =
        isJunk(name) || isForeignMetadata(name) || isStaleIncompleteDownload(name, modified, now)

    /** Leftover app installers, worth reviewing for removal once the app is installed. */
    fun isInstaller(name: String?): Boolean {
        val lower = name?.lowercase() ?: return false
        return lower.endsWith(".apk") || lower.endsWith(".xapk") ||
            lower.endsWith(".apkm") || lower.endsWith(".obb")
    }

    /** Plain APKs, the only installer format Android can read the package and version from. */
    fun isApk(name: String?): Boolean = name?.lowercase()?.endsWith(".apk") == true

    /** An installer is redundant once the installed app is at least the installer's version. */
    fun isInstallerRedundant(archiveVersion: Long, installedVersion: Long?): Boolean =
        installedVersion != null && installedVersion >= archiveVersion

    const val STALE_INCOMPLETE_DAYS = 7L
}

class TeeVRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("teevclean_prefs", Context.MODE_PRIVATE)

    fun getCustomFolders(): List<String> =
        prefs.getStringSet(KEY_CUSTOM_FOLDERS, emptySet()).orEmpty().toList()

    fun addCustomFolder(uri: String) {
        prefs.edit { putStringSet(KEY_CUSTOM_FOLDERS, getCustomFolders().toSet() + uri) }
    }

    /** Forgets a folder and releases its persisted grant, which otherwise counts toward the system cap. */
    fun removeCustomFolder(uri: String) {
        prefs.edit { putStringSet(KEY_CUSTOM_FOLDERS, getCustomFolders().toSet() - uri) }
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // The grant was already revoked or never persisted.
        }
    }

    fun hasFullStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Total and available bytes of the data partition. */
    private fun dataPartitionSpace(): Pair<Long, Long> {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.totalBytes to stat.availableBytes
    }

    suspend fun readStorage(): StorageSummary = withContext(Dispatchers.IO) {
        val (total, free) = dataPartitionSpace()
        StorageSummary((total - free).coerceIn(0L, total), total)
    }

    /** Installed apps the user can remove, excluding system apps and TeeVClean itself. */
    private fun userApps(): List<ApplicationInfo> =
        context.packageManager.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }

    private fun storageStatsManager(): StorageStatsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(StorageStatsManager::class.java)
        } else {
            null
        }

    private class PackageBytes(val total: Long, val cache: Long)

    /**
     * Real footprint of an app (installed code + data, cache included) via StorageStatsManager.
     * Null when unavailable: below API 26, or without usage access for other packages.
     */
    private fun packageBytes(statsManager: StorageStatsManager?, packageName: String): PackageBytes? {
        if (statsManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            val stats = statsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, packageName, Process.myUserHandle())
            // dataBytes already includes cacheBytes, so cache is not added on top.
            PackageBytes(stats.appBytes + stats.dataBytes, stats.cacheBytes)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Breaks device storage into user-app usage (with its cache subtotal) and everything else.
     * App attribution needs usage access + API 26; without it [StorageBreakdown.appDataKnown] is
     * false and only total/free are meaningful.
     */
    suspend fun storageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        val (total, free) = dataPartitionSpace()
        val statsManager = storageStatsManager()
            ?: return@withContext StorageBreakdown(total, free, 0L, 0L, false)

        val apps = userApps()
        val perApp = coroutineScope {
            apps.map { app -> async { packageBytes(statsManager, app.packageName) } }.awaitAll()
        }.filterNotNull()

        StorageBreakdown(
            totalBytes = total,
            freeBytes = free,
            appsBytes = perApp.sumOf { it.total },
            appCacheBytes = perApp.sumOf { it.cache },
            // Every query fails without usage access; report that instead of a misleading 0 B.
            appDataKnown = perApp.isNotEmpty() || apps.isEmpty(),
        )
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
        ownCacheDirs().sumOf { folderSize(it) }
    }

    suspend fun clearOwnCache(): CleanupResult = withContext(Dispatchers.IO) {
        var freed = 0L
        var removed = 0
        ownCacheDirs().forEach { dir ->
            dir.listFiles()?.forEach { entry ->
                val entrySize = folderSize(entry)
                if (entry.deleteRecursively()) {
                    freed += entrySize
                    removed++
                }
            }
        }
        CleanupResult(freed, removed).also { recordCleanup(it) }
    }

    suspend fun scanLargeFiles(): List<FileSummary> = withContext(Dispatchers.IO) {
        val filter = getFileFilter()
        val now = System.currentTimeMillis()

        // Public storage roots read directly (works where the app has legacy/all-files access).
        val publicRoots = buildList {
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
            // With full storage access (legacy or MANAGE_EXTERNAL_STORAGE), include the storage root too.
            if (hasFullStorageAccess()) add(Environment.getExternalStorageDirectory())
        }

        val publicFiles = publicRoots.distinct().flatMap { root ->
            root.listFiles().orEmpty().filter { it.isFile }.map { file ->
                FileSummary(file.name, file.parent.orEmpty(), file.length(), file.lastModified(), Uri.fromFile(file).toString())
            }
        }

        // Folders the user granted through the Storage Access Framework; entries here are deletable.
        val safFiles = grantedSafFiles().map { it.toSummary() }

        (publicFiles + safFiles)
            .distinctBy { it.uri }
            .mapNotNull { file ->
                // APKs are checked against installed apps even when the filter wouldn't list them,
                // so a redundant installer always shows up under "Already installed".
                val checked = if (FileClassifier.isApk(file.name)) {
                    file.copy(alreadyInstalled = isInstalledApk(file.uri))
                } else {
                    file
                }
                checked.takeIf { it.alreadyInstalled || filter.matches(it.name, it.size, it.modified, now) }
            }
            .sortedByDescending { it.size }
    }

    /**
     * True when the APK behind [uriString] belongs to an app that is already installed at the
     * same or a newer version. SAF documents have no file path, so they are read through their
     * descriptor's /proc path. Any failure means "not known to be installed".
     */
    private fun isInstalledApk(uriString: String): Boolean = try {
        val uri = uriString.toUri()
        when (uri.scheme) {
            "file" -> uri.path?.let { isInstalledArchive(it) } == true
            "content" -> context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                isInstalledArchive("/proc/self/fd/${pfd.fd}")
            } == true
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    private fun isInstalledArchive(path: String): Boolean {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(path, 0) ?: return false
        val installed = try {
            PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(archive.packageName, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return FileClassifier.isInstallerRedundant(PackageInfoCompat.getLongVersionCode(archive), installed)
    }

    /** A document found while walking a granted SAF tree. */
    private class SafEntry(
        val uri: Uri,
        val documentId: String,
        val parentId: String,
        val name: String,
        val parentName: String,
        val mimeType: String?,
        val size: Long,
        val modified: Long,
    ) {
        val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
        val isFile: Boolean get() = !isDirectory && !mimeType.isNullOrEmpty()

        /** Same document reached through overlapping trees (a folder and its sub-folder) shares this. */
        val identity: String get() = "${uri.authority}/$documentId"

        fun toSummary() = FileSummary(name, parentName, size, modified, uri.toString())
    }

    /**
     * Bounded breadth-first walk over a granted SAF tree. Each directory is listed with one
     * DocumentsContract query returning name, type, size and date together, instead of the
     * several IPC round-trips per file that DocumentFile needs. [MAX_SCAN_NODES] keeps a
     * pathological tree from stalling the scan; [completeDirs], when given, receives the ids of
     * directories whose children were all listed.
     */
    private fun walkSafTree(treeUriString: String, completeDirs: MutableSet<String>? = null): List<SafEntry> {
        val treeUri = treeUriString.toUri()
        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            return emptyList()
        }

        val resolver = context.contentResolver
        val entries = mutableListOf<SafEntry>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootId to documentName(treeUri, rootId).orEmpty())
        while (queue.isNotEmpty() && entries.size < MAX_SCAN_NODES) {
            val (dirId, dirName) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
            val listedAll = try {
                resolver.query(childrenUri, SAF_PROJECTION, null, null, null)?.use { cursor ->
                    var complete = true
                    while (cursor.moveToNext()) {
                        if (entries.size >= MAX_SCAN_NODES) {
                            complete = false
                            break
                        }
                        val id = cursor.getString(0) ?: continue
                        val entry = SafEntry(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            documentId = id,
                            parentId = dirId,
                            name = cursor.getString(1) ?: "unknown",
                            parentName = dirName,
                            mimeType = cursor.getString(2),
                            size = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                            modified = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                        )
                        entries.add(entry)
                        if (entry.isDirectory) queue.add(id to entry.name)
                    }
                    complete
                } ?: false
            } catch (_: Exception) {
                false
            }
            if (listedAll) completeDirs?.add(dirId)
        }
        return entries
    }

    private fun documentName(treeUri: Uri, documentId: String): String? = try {
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
            arrayOf(Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    /**
     * Every file in the granted folders, each listed once even when the user added both a
     * folder and one of its sub-folders. Without that, the same file would show up twice and
     * be offered as its own "duplicate".
     */
    private fun grantedSafFiles(): List<SafEntry> =
        getCustomFolders()
            .flatMap { walkSafTree(it) }
            .filter { it.isFile }
            .distinctBy { it.identity }

    private fun deleteDocument(uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(context.contentResolver, uri)
    } catch (_: Exception) {
        false
    }

    /** Deletes a single scanned file by its handle. Content Uris use SAF; file Uris use direct IO. */
    private fun deleteFile(uriString: String): Boolean {
        val uri = try {
            uriString.toUri()
        } catch (_: Exception) {
            return false
        }
        return when (uri.scheme) {
            "content" -> deleteDocument(uri)
            "file" -> try {
                uri.path?.let { File(it).delete() } == true
            } catch (_: Exception) {
                false
            }
            else -> false
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
     * Files are grouped by exact size and then by a content fingerprint (see [fileFingerprint]).
     * Deletion is always user-confirmed.
     */
    suspend fun scanDuplicates(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val sameSize = grantedSafFiles()
            .filter { it.size >= MIN_DUPLICATE_BYTES }
            .map { it.toSummary() }
            .groupBy { it.size }
            .values
            .filter { it.size > 1 }
            .flatten()

        val fingerprinted = coroutineScope {
            sameSize.map { file -> async { fileFingerprint(file)?.let { (file.size to it) to file } } }.awaitAll()
        }

        fingerprinted
            .filterNotNull()
            .groupBy({ it.first }, { it.second })
            .values
            .filter { it.size > 1 }
            .map { group -> DuplicateGroup(group.sortedByDescending { it.modified }, group.first().size) }
            .sortedByDescending { it.reclaimableBytes }
    }

    /**
     * SHA-256 over the first and last [FINGERPRINT_BYTES] of a file (the whole file when it is
     * smaller). Sampling both ends, on top of the exact size match, keeps hashing fast without
     * matching files that merely share a common header. Returns null when the file can't be
     * read as reported, which excludes it from duplicate detection rather than risk a false match.
     */
    private fun fileFingerprint(file: FileSummary): String? = try {
        context.contentResolver.openFileDescriptor(file.uri.toUri(), "r")?.let { pfd ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
                val channel = stream.channel
                val size = channel.size()
                if (size != file.size) return@use null
                val digest = MessageDigest.getInstance("SHA-256")
                val head = minOf(size, FINGERPRINT_BYTES)
                if (!digestRange(channel, 0, head, digest)) return@use null
                if (size > head) {
                    val tailStart = maxOf(head, size - FINGERPRINT_BYTES)
                    if (!digestRange(channel, tailStart, size - tailStart, digest)) return@use null
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }
    } catch (_: Exception) {
        null
    }

    /** Feeds [length] bytes from [start] into [digest]; false if the file ended early. */
    private fun digestRange(channel: FileChannel, start: Long, length: Long, digest: MessageDigest): Boolean {
        val buffer = ByteBuffer.allocate(64 * 1024)
        val end = start + length
        var position = start
        while (position < end) {
            buffer.clear()
            buffer.limit(minOf(buffer.capacity().toLong(), end - position).toInt())
            val read = channel.read(buffer, position)
            if (read <= 0) return false
            buffer.flip()
            digest.update(buffer)
            position += read
        }
        return true
    }

    /**
     * Removes obvious junk from granted SAF folders: temp/log/thumbnail files, other computers'
     * drive metadata (whole folders such as `.Trashes` included), stale torrent pieces, and
     * folders left empty.
     */
    suspend fun sweepJunk(): CleanupResult = withContext(Dispatchers.IO) {
        var freed = 0L
        var removed = 0
        val now = System.currentTimeMillis()
        getCustomFolders().forEach { treeUriString ->
            val completeDirs = mutableSetOf<String>()
            val entries = walkSafTree(treeUriString, completeDirs)
            val byId = entries.associateBy { it.documentId }
            // Children left in each folder, so a folder emptied by this sweep is removed as well.
            val remaining = entries.groupingBy { it.parentId }.eachCount().toMutableMap()

            // Bytes under each folder, summed children-first (reversed breadth-first order).
            val subtreeBytes = HashMap<String, Long>()
            for (entry in entries.asReversed()) {
                val own = if (entry.isFile) entry.size else subtreeBytes[entry.documentId] ?: 0L
                subtreeBytes[entry.parentId] = (subtreeBytes[entry.parentId] ?: 0L) + own
            }

            // Junk folders go first, top-down, each in one delete with everything inside it.
            val deletedDirs = mutableSetOf<String>()
            fun insideDeleted(entry: SafEntry): Boolean {
                var id: String? = entry.parentId
                while (id != null) {
                    if (id in deletedDirs) return true
                    id = byId[id]?.parentId
                }
                return false
            }
            for (entry in entries) {
                if (!entry.isDirectory || !FileClassifier.isJunkFolder(entry.name) || insideDeleted(entry)) continue
                if (deleteDocument(entry.uri)) {
                    deletedDirs += entry.documentId
                    freed += subtreeBytes[entry.documentId] ?: 0L
                    removed++
                    remaining[entry.parentId] = (remaining[entry.parentId] ?: 1) - 1
                }
            }

            // The walk is breadth-first, so reversed it is deepest-first: children before their folder.
            for (entry in entries.asReversed()) {
                if (entry.documentId in deletedDirs || insideDeleted(entry)) continue
                val deleted = when {
                    entry.isFile && FileClassifier.isSweepableFile(entry.name, entry.modified, now) ->
                        deleteDocument(entry.uri).also { if (it) freed += entry.size }
                    entry.isDirectory && entry.documentId in completeDirs && (remaining[entry.documentId] ?: 0) == 0 ->
                        deleteDocument(entry.uri)
                    else -> false
                }
                if (deleted) {
                    removed++
                    remaining[entry.parentId] = (remaining[entry.parentId] ?: 1) - 1
                }
            }
        }
        CleanupResult(freed, removed).also { recordCleanup(it) }
    }

    /** Free bytes on the data partition right now; used to measure what a system cleanup freed. */
    fun freeBytes(): Long = dataPartitionSpace().second

    /**
     * Adds a cleanup done by Android itself (e.g. the clear-all-caches prompt) to the history.
     * The system doesn't report an item count, so only the bytes are added.
     */
    fun recordSystemCleanup(freedBytes: Long) = recordCleanup(CleanupResult(freedBytes, 0))

    /** Opens the system "free up space" storage manager so the user can act device-wide. */
    fun openStorageManager() {
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Intent(StorageManager.ACTION_MANAGE_STORAGE)
        } else {
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        }
        if (!startActivitySafely(primary)) {
            startActivitySafely(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        }
    }

    /** Most recent use of each package over the last [LAST_USED_WINDOW_DAYS]; empty without usage access. */
    private fun readLastUsedByPackage(): Map<String, Long> {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        return try {
            // Aggregation merges the per-interval buckets and keeps the latest lastTimeUsed per package.
            usage.queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(LAST_USED_WINDOW_DAYS), now)
                .mapValues { it.value.lastTimeUsed }
        } catch (_: SecurityException) {
            emptyMap()
        }
    }

    suspend fun loadApps(): List<AppSummary> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val lastUsed = readLastUsedByPackage()
        val statsManager = storageStatsManager()

        coroutineScope {
            userApps().map { app ->
                async {
                    // Fall back to the APK size when the full footprint is unavailable.
                    val size = packageBytes(statsManager, app.packageName)?.total
                        ?: app.sourceDir?.let { File(it).length() }
                        ?: 0L
                    AppSummary(app.loadLabel(pm).toString(), app.packageName, size, lastUsed[app.packageName] ?: 0L)
                }
            }.awaitAll()
        }.sortedByDescending { it.size }
    }

    fun getCleanupFrequency(): CleanupFrequency =
        runCatching {
            CleanupFrequency.valueOf(prefs.getString(KEY_FREQUENCY, null) ?: CleanupFrequency.OFF.name)
        }.getOrDefault(CleanupFrequency.OFF)

    fun isScheduledSweepEnabled(): Boolean = prefs.getBoolean(KEY_INCLUDE_SWEEP, false)

    /**
     * Configures the recurring background cleanup. Only unattended-safe actions are ever
     * scheduled: the app's own cache always, and - when [includeSweep] is set - the temp/log
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
            manager.cancelUniqueWork(CLEANUP_WORK)
        } else {
            val request = PeriodicWorkRequestBuilder<CleanupWorker>(intervalDays, TimeUnit.DAYS)
                .setInputData(workDataOf(INCLUDE_SWEEP to includeSweep))
                .build()
            manager.enqueueUniquePeriodicWork(CLEANUP_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    fun openAppInfo(packageName: String) {
        startActivitySafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }

    /** Launches the system uninstall prompt for a package. The user confirms the removal. */
    fun uninstallApp(packageName: String) {
        if (!startActivitySafely(Intent(Intent.ACTION_DELETE, "package:$packageName".toUri()))) {
            openAppInfo(packageName)
        }
    }

    private fun startActivitySafely(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        // No activity handles this intent on this device.
        false
    }

    fun getFileFilter(): FileFilter {
        val defaults = FileFilter()
        return FileFilter(
            minSize = prefs.getLong(KEY_MIN_FILE_SIZE, defaults.minSize),
            minAgeDays = prefs.getInt(KEY_MIN_FILE_AGE, defaults.minAgeDays),
            showInstallers = prefs.getBoolean(KEY_SHOW_INSTALLERS, defaults.showInstallers),
        )
    }

    fun setFileFilter(filter: FileFilter) {
        prefs.edit {
            putLong(KEY_MIN_FILE_SIZE, filter.minSize)
            putInt(KEY_MIN_FILE_AGE, filter.minAgeDays)
            putBoolean(KEY_SHOW_INSTALLERS, filter.showInstallers)
        }
    }

    private fun folderSize(root: File): Long =
        root.walk().filter { it.isFile }.sumOf { it.length() }

    companion object {
        /** Unique WorkManager name; the value predates daily/monthly schedules and must stay stable. */
        const val CLEANUP_WORK = "weekly-cache-cleanup"
        const val INCLUDE_SWEEP = "include_sweep"
        private const val MAX_SCAN_NODES = 5000
        private const val LAST_USED_WINDOW_DAYS = 180L
        private const val KEY_CUSTOM_FOLDERS = "custom_folders"
        private const val KEY_FREQUENCY = "cleanup_frequency"
        private const val KEY_INCLUDE_SWEEP = "cleanup_include_sweep"
        private const val KEY_LAST_RUN = "cleanup_last_run"
        private const val KEY_TOTAL_FREED = "cleanup_total_freed"
        private const val KEY_TOTAL_ITEMS = "cleanup_total_items"
        private const val KEY_MIN_FILE_SIZE = "min_file_size"
        private const val KEY_MIN_FILE_AGE = "min_file_age"
        private const val KEY_SHOW_INSTALLERS = "show_installers"

        private val SAF_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )

        /** Ignore files below this size when hunting duplicates - tiny files aren't worth the churn. */
        private const val MIN_DUPLICATE_BYTES = 1L * 1024 * 1024

        /** Bytes hashed from each end of a file to fingerprint its content for duplicate detection. */
        private const val FINGERPRINT_BYTES = 1L * 1024 * 1024
    }
}
