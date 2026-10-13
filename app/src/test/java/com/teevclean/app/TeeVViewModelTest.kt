package com.teevclean.app

import android.app.Application
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeeVViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val application: Application = mockk(relaxed = true)
    private val repository: TeeVRepository = mockk(relaxed = true)

    private val app = AppSummary("Player", "com.example.player", 2048, 0)
    private val file = FileSummary("movie.mkv", "Movies", 4096, 0, "content://docs/movie")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getCustomFolders() } returns listOf("content://tree/a")
        every { repository.getCleanupFrequency() } returns CleanupFrequency.WEEKLY
        every { repository.isScheduledSweepEnabled() } returns true
        every { repository.getCleanupHistory() } returns CleanupHistory(1L, 10L, 1)
        every { repository.getFileFilter() } returns FileFilter()
        coEvery { repository.readStorage() } returns StorageSummary(used = 40, total = 100)
        coEvery { repository.loadApps() } returns listOf(app)
        coEvery { repository.scanLargeFiles() } returns listOf(file)
        coEvery { repository.getCacheSize() } returns 123L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TeeVViewModel(application, repository)

    @Test
    fun `initial state comes from persisted settings`() {
        val vm = viewModel()
        assertEquals(Screen.OVERVIEW, vm.currentScreen)
        assertEquals(CleanupFrequency.WEEKLY, vm.cleanupFrequency)
        assertTrue(vm.scheduleSweepEnabled)
        assertEquals(listOf("content://tree/a"), vm.customFolders)
    }

    @Test
    fun `refreshData loads storage apps files and cache`() {
        val vm = viewModel()
        vm.refreshData()
        assertTrue(vm.isRefreshing)

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.isRefreshing)
        assertEquals(StorageSummary(40, 100), vm.storageSummary)
        assertEquals(listOf(app), vm.apps)
        assertEquals(listOf(file), vm.largeFiles)
        assertEquals(123L, vm.cacheSize)
    }

    @Test
    fun `deleting files clears stale duplicate groups and reports the result`() {
        coEvery { repository.scanDuplicates() } returns listOf(DuplicateGroup(listOf(file, file.copy(uri = "content://docs/copy")), 4096))
        coEvery { repository.deleteFiles(any()) } returns CleanupResult(4096, 1)
        val vm = viewModel()
        vm.findDuplicates()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.duplicateGroups.size)

        var reported: CleanupResult? = null
        vm.deleteFiles(listOf(file)) { reported = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(CleanupResult(4096, 1), reported)
        assertTrue(vm.duplicateGroups.isEmpty())
    }

    @Test
    fun `duplicate scan is not started twice while one is running`() {
        coEvery { repository.scanDuplicates() } returns emptyList()
        val vm = viewModel()
        vm.findDuplicates()
        vm.findDuplicates()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.scanDuplicates() }
        assertFalse(vm.isScanningDuplicates)
    }

    @Test
    fun `updating the file filter persists it and rescans`() {
        val vm = viewModel()
        val filter = FileFilter(minSize = 1024, minAgeDays = 7, showInstallers = false)

        vm.updateFileFilter(filter)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { repository.setFileFilter(filter) }
        assertEquals(filter, vm.fileFilter)
        coVerify { repository.scanLargeFiles() }
    }
}
