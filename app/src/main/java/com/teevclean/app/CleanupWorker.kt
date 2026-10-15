package com.teevclean.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class CleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        try {
            // Only unattended-safe actions run here - never user files or other apps' caches.
            val repository = TeeVRepository(applicationContext)
            val kinds = mutableListOf(CleanupKind.OWN_CACHE)
            val results = mutableListOf(repository.clearOwnCache())
            if (inputData.getBoolean(TeeVRepository.INCLUDE_SWEEP, false)) {
                kinds += CleanupKind.SWEEP_FOLDERS
                results += repository.sweepJunk()
            }
            val sweepDevice = inputData.getBoolean(TeeVRepository.INCLUDE_DEVICE_SWEEP, false)
            val trash = inputData.getBoolean(TeeVRepository.INCLUDE_TRASH, false)
            if (sweepDevice || trash) {
                if (sweepDevice) kinds += CleanupKind.SWEEP_DEVICE
                if (trash) kinds += CleanupKind.TRASH
                results += repository.cleanDeviceStorage(sweep = sweepDevice, trash = trash)
            }
            if (inputData.getBoolean(TeeVRepository.INCLUDE_INSTALLED_APKS, false)) {
                kinds += CleanupKind.INSTALLERS
                results += repository.removeInstalledApks()
            }
            // One history entry for the whole run, logged even when nothing was found, so the
            // log shows the schedule is running.
            repository.recordCleanup(
                kinds,
                CleanupResult(results.sumOf { it.freedBytes }, results.sumOf { it.itemsRemoved }),
                automatic = true,
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CleanupWorker", "Error during scheduled cleanup", e)
            Result.retry()
        }
}
