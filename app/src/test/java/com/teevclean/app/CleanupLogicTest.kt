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
}
