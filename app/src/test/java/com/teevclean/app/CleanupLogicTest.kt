package com.teevclean.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic unit tests - no Android framework needed. */
class CleanupLogicTest {

    @Test
    fun `isJunk matches temp log thumbnail and partial downloads`() {
        listOf("a.tmp", "b.TEMP", "c.log", "thumbs.db", ".DS_Store", "x.thumbnails", "d.part", "e.crdownload", "f.download")
            .forEach { assertTrue(it, FileClassifier.isJunk(it)) }
    }

    @Test
    fun `isJunk ignores normal files and null`() {
        assertFalse(FileClassifier.isJunk("movie.mp4"))
        assertFalse(FileClassifier.isJunk("notes.txt"))
        assertFalse(FileClassifier.isJunk(null))
    }

    @Test
    fun `isInstaller matches apk variants case-insensitively`() {
        listOf("app.apk", "APP.APK", "bundle.xapk", "mod.apkm", "data.obb")
            .forEach { assertTrue(it, FileClassifier.isInstaller(it)) }
        assertFalse(FileClassifier.isInstaller("readme.pdf"))
        assertFalse(FileClassifier.isInstaller(null))
    }

    @Test
    fun `formatBytes renders human units`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `cleanup frequency maps to intervals`() {
        assertEquals(null, CleanupFrequency.OFF.intervalDays)
        assertEquals(1L, CleanupFrequency.DAILY.intervalDays)
        assertEquals(7L, CleanupFrequency.WEEKLY.intervalDays)
        assertEquals(30L, CleanupFrequency.MONTHLY.intervalDays)
    }

    @Test
    fun `storage summary computes free and fraction with clamping`() {
        val half = StorageSummary(used = 50, total = 100)
        assertEquals(50L, half.free)
        assertEquals(0.5f, half.fraction, 0.0001f)

        assertEquals(0f, StorageSummary(used = 10, total = 0).fraction, 0.0001f)
        assertEquals(1f, StorageSummary(used = 200, total = 100).fraction, 0.0001f)
        assertEquals(0L, StorageSummary(used = 200, total = 100).free)
    }

    @Test
    fun `duplicate group reclaimable excludes the kept copy`() {
        fun file(uri: String) = FileSummary("n", "p", 100, 0, uri)
        val group = DuplicateGroup(listOf(file("a"), file("b"), file("c")), sizeEach = 100)
        assertEquals(200L, group.reclaimableBytes)
        assertEquals(0L, DuplicateGroup(listOf(file("only")), sizeEach = 100).reclaimableBytes)
    }

    @Test
    fun `storage breakdown derives used and system-other`() {
        val b = StorageBreakdown(totalBytes = 1000, freeBytes = 400, appsBytes = 350, appCacheBytes = 50, appDataKnown = true)
        assertEquals(600L, b.usedBytes)
        assertEquals(250L, b.systemAndOtherBytes)

        // Apps larger than used (rounding/attribution) never goes negative.
        val odd = StorageBreakdown(totalBytes = 1000, freeBytes = 400, appsBytes = 900, appCacheBytes = 0, appDataKnown = true)
        assertEquals(0L, odd.systemAndOtherBytes)
    }

    @Test
    fun `file filter matches large old or installer files`() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val filter = FileFilter(minSize = 1000, minAgeDays = 30, showInstallers = true)

        assertTrue(filter.matches("big.mkv", size = 1000, modified = now, now = now))
        assertTrue(filter.matches("old.txt", size = 1, modified = now - 31 * day, now = now))
        assertTrue(filter.matches("app.apk", size = 1, modified = now, now = now))
        assertFalse(filter.matches("new.txt", size = 1, modified = now - 29 * day, now = now))
        assertFalse(filter.copy(showInstallers = false).matches("app.apk", size = 1, modified = now, now = now))
    }

    @Test
    fun `folder display name decodes the tree id`() {
        assertEquals("Movies/TV", folderDisplayName("content://com.android.externalstorage.documents/tree/primary%3AMovies%2FTV"))
        assertEquals("Download", folderDisplayName("content://com.android.externalstorage.documents/tree/primary%3ADownload"))
        assertEquals("1234-ABCD", folderDisplayName("content://com.android.externalstorage.documents/tree/1234-ABCD%3A"))
        assertEquals("not-a-tree", folderDisplayName("not-a-tree"))
    }

    @Test
    fun `foreign drive metadata is matched but similar names are not`() {
        listOf("._movie.mp4", "._", "desktop.ini", "Desktop.INI").forEach { assertTrue(it, FileClassifier.isForeignMetadata(it)) }
        listOf("_movie.mp4", ".hidden", "desktop.ini.txt", null).forEach { assertFalse("$it", FileClassifier.isForeignMetadata(it)) }
    }

    @Test
    fun `junk folders cover os index trash and thumbnail caches`() {
        listOf(".thumbnails", ".Spotlight-V100", ".Trashes", ".fseventsd", ".Trash-1000").forEach {
            assertTrue(it, FileClassifier.isJunkFolder(it))
        }
        listOf("Trash", "thumbnails", ".trash", "Movies", null).forEach { assertFalse("$it", FileClassifier.isJunkFolder(it)) }
    }

    @Test
    fun `torrent pieces only count once stale`() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        assertTrue(FileClassifier.isStaleIncompleteDownload("show.mkv.!qB", now - 8 * day, now))
        assertTrue(FileClassifier.isStaleIncompleteDownload("show.mkv.!ut", now - 8 * day, now))
        assertFalse(FileClassifier.isStaleIncompleteDownload("show.mkv.!qB", now - 1 * day, now))
        assertFalse("unknown date is never stale", FileClassifier.isStaleIncompleteDownload("show.mkv.!qB", 0, now))
        assertFalse(FileClassifier.isStaleIncompleteDownload("show.mkv", now - 8 * day, now))
    }

    @Test
    fun `sweepable files combine all file rules`() {
        val now = 100L * 24 * 60 * 60 * 1000
        assertTrue(FileClassifier.isSweepableFile("a.tmp", now, now))
        assertTrue(FileClassifier.isSweepableFile("._a.jpg", now, now))
        assertFalse(FileClassifier.isSweepableFile("a.jpg", now, now))
    }

    @Test
    fun `trashed media is matched by android's trash prefix only`() {
        assertTrue(FileClassifier.isTrashed(".trashed-1735689600-IMG_0001.jpg"))
        assertTrue(FileClassifier.isTrashed(".TRASHED-1735689600-clip.mp4"))
        listOf("trashed-1-a.jpg", ".trash-1000", "IMG_0001.jpg", null).forEach { assertFalse("$it", FileClassifier.isTrashed(it)) }
    }

    @Test
    fun `files only count as settled after a day untouched`() {
        val hour = 60L * 60 * 1000
        val now = 1000 * hour
        assertTrue(FileClassifier.hasSettled(now - 25 * hour, now))
        assertFalse("still being written", FileClassifier.hasSettled(now - 1 * hour, now))
        assertFalse("unknown date is never settled", FileClassifier.hasSettled(0, now))
    }

    @Test
    fun `schedule options count the opted-in extras`() {
        assertEquals(0, ScheduleOptions().extrasCount)
        assertEquals(2, ScheduleOptions(sweepDevice = true, trash = true).extrasCount)
        assertEquals(4, ScheduleOptions(sweepFolders = true, sweepDevice = true, installedApks = true, trash = true).extrasCount)
    }

    @Test
    fun `cleanup log survives an encode and decode round trip`() {
        val entries = listOf(
            CleanupLogEntry(2000L, true, listOf(CleanupKind.OWN_CACHE, CleanupKind.SWEEP_DEVICE, CleanupKind.TRASH), 5_000_000L, 12),
            CleanupLogEntry(1000L, false, listOf(CleanupKind.APP_CACHES), 0L, 0),
        )
        assertEquals(entries, CleanupLogCodec.decode(CleanupLogCodec.encode(entries)))
        assertEquals(emptyList<CleanupLogEntry>(), CleanupLogCodec.decode(null))
        assertEquals(emptyList<CleanupLogEntry>(), CleanupLogCodec.decode(""))
    }

    @Test
    fun `cleanup log skips malformed lines and unknown kinds`() {
        val decoded = CleanupLogCodec.decode("garbage\n10;1;OWN_CACHE,FUTURE_KIND;50;1\nx;0;TRASH;1;1")
        assertEquals(listOf(CleanupLogEntry(10L, true, listOf(CleanupKind.OWN_CACHE), 50L, 1)), decoded)
    }

    @Test
    fun `cleanup log keeps the newest entries up to the limit`() {
        fun entry(time: Long) = CleanupLogEntry(time, false, listOf(CleanupKind.FILES), 1L, 1)
        val log = (1L..3L).fold(emptyList<CleanupLogEntry>()) { acc, t -> CleanupLogCodec.prepend(acc, entry(t), limit = 2) }
        assertEquals(listOf(entry(3L), entry(2L)), log)
    }

    @Test
    fun `installer is redundant only when installed at same or newer version`() {
        assertTrue(FileClassifier.isInstallerRedundant(archiveVersion = 10, installedVersion = 10))
        assertTrue(FileClassifier.isInstallerRedundant(archiveVersion = 10, installedVersion = 12))
        assertFalse("newer installer is an update", FileClassifier.isInstallerRedundant(archiveVersion = 12, installedVersion = 10))
        assertFalse("not installed", FileClassifier.isInstallerRedundant(archiveVersion = 10, installedVersion = null))
        assertTrue(FileClassifier.isApk("App.APK"))
        assertFalse(FileClassifier.isApk("bundle.xapk"))
    }
}
