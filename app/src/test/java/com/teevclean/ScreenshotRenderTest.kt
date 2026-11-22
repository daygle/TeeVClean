package com.teevclean

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Renders the real app UI headlessly (Robolectric + native graphics) for the Play listing.
 *
 * These are Compose renders, not device captures: fonts, system bars and some density details
 * can differ from a TV. They are written to store-listing/screenshots-rendered/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1440dp-h810dp-tvdpi")
class ScreenshotRenderTest {
    @get:Rule
    val compose = createComposeRule()

    // Real clock: the listing should show dates close to the release, not a fixed past date.
    private val now = System.currentTimeMillis()
    private val gb = 1024L * 1024 * 1024
    private val mb = 1024L * 1024

    @Test
    fun renderStoreScreens() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val repository = mockk<TeeVRepository>(relaxed = true)

        every { repository.getCustomFolders() } returns emptyList()
        every { repository.getCleanupFrequency() } returns CleanupFrequency.WEEKLY
        every { repository.getScheduleOptions() } returns ScheduleOptions(sweepFolders = true, installedApks = true, trash = true)
        every { repository.getCleanupHistory() } returns CleanupHistory(now - TimeUnit.DAYS.toMillis(2), 9_200_000_000L, 318)
        every { repository.getCleanupLog() } returns emptyList()
        every { repository.getLastAutomaticRun() } returns now - TimeUnit.DAYS.toMillis(2)
        every { repository.getScheduleMinuteOfDay() } returns 3 * 60
        every { repository.getFileFilter() } returns FileFilter()
        every { repository.hasFullStorageAccess() } returns true
        coEvery { repository.unreachableFolders(any()) } returns emptySet()
        coEvery { repository.readStorage() } returns
            StorageSummary(used = 26_400_000_000L, total = 34_359_738_368L)
        coEvery { repository.getNextScheduledRun(any()) } returns null
        coEvery { repository.loadApps() } returns listOf(
            AppSummary("Kodi", "org.xbmc.kodi", 1_640L * mb, now - TimeUnit.DAYS.toMillis(1)),
            AppSummary("YouTube", "com.google.android.youtube.tv", 1_210L * mb, now - TimeUnit.HOURS.toMillis(2)),
            AppSummary("Netflix", "com.netflix.ninja", 890L * mb, now - TimeUnit.HOURS.toMillis(5)),
            AppSummary("Prime Video", "com.amazon.amazonvideo.livingroom", 640L * mb, now - TimeUnit.DAYS.toMillis(3)),
            AppSummary("Disney+", "com.disney.disneyplus", 512L * mb, now - TimeUnit.DAYS.toMillis(100)),
            AppSummary("Plex", "com.plexapp.android", 380L * mb, now - TimeUnit.DAYS.toMillis(140)),
            AppSummary("Spotify", "com.spotify.tv.android", 210L * mb, now - TimeUnit.DAYS.toMillis(120)),
            AppSummary("VLC", "org.videolan.vlc", 96L * mb, 0L),
        )
        coEvery { repository.scanLargeFiles() } returns listOf(
            FileSummary("Backup 2023.mkv", "Download", 4_100L * mb, now - TimeUnit.DAYS.toMillis(210), "content://docs/1"),
            FileSummary("Holiday 2019.mp4", "Movies", 1_800L * mb, now - TimeUnit.DAYS.toMillis(420), "content://docs/2"),
            FileSummary("screen-recording.mp4", "Pictures", 780L * mb, now - TimeUnit.DAYS.toMillis(45), "content://docs/3"),
            FileSummary("kodi-20.5.apk", "Download", 92L * mb, now - TimeUnit.DAYS.toMillis(60), "content://docs/4"),
            FileSummary("netflix-old.apk", "Download", 48L * mb, now - TimeUnit.DAYS.toMillis(300), "content://docs/5", alreadyInstalled = true),
        )

        // Usage access granted, so Applications shows real sizes and last-used dates (as it does
        // for a user who enabled it) instead of the onboarding card.
        val appOps = application.getSystemService(android.app.AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            application.packageName,
            android.app.AppOpsManager.MODE_ALLOWED,
        )

        val viewModel = TeeVViewModel(application, repository)
        // A no-op prompt keeps the Android 11+ "All app caches" card in the Clean-up render.
        compose.setContent { TeeVCleanApp(viewModel, onRequestStorageAccess = {}, onAddFolder = {}, onClearAllCaches = { _ -> }) }
        compose.waitForIdle()

        capture("overview", viewModel) { it.currentScreen = Screen.OVERVIEW }
        capture("files", viewModel) { it.currentScreen = Screen.LARGE }
        capture("cleanup", viewModel) { it.currentScreen = Screen.CLEAN }
        capture("applications", viewModel) { it.currentScreen = Screen.APPS }
        capture("settings", viewModel) { it.currentScreen = Screen.SETTINGS }
    }

    private fun capture(name: String, viewModel: TeeVViewModel, select: (TeeVViewModel) -> Unit) {
        select(viewModel)
        compose.waitForIdle()
        val raw = compose.onRoot().captureToImage().asAndroidBitmap()
        val exact = Bitmap.createScaledBitmap(raw, 1920, 1080, true)
        val out = File(repoRoot(), "store-listing/screenshots-rendered/$name.png")
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { exact.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Play wants screenshots without an alpha channel; re-encode the fully opaque bitmap.
        val rendered = javax.imageio.ImageIO.read(out)
        val rgb = java.awt.image.BufferedImage(rendered.width, rendered.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().apply { drawImage(rendered, 0, 0, java.awt.Color.BLACK, null); dispose() }
        javax.imageio.ImageIO.write(rgb, "png", out)
        println("RENDER $name ${exact.width}x${exact.height} RGB -> ${out.path}")
    }

    private fun repoRoot(): File {
        val dir = File(System.getProperty("user.dir")).absoluteFile
        return if (dir.name == "app") dir.parentFile else dir
    }
}
