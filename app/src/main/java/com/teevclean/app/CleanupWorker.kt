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
            repository.clearOwnCache()
            if (inputData.getBoolean(TeeVRepository.INCLUDE_SWEEP, false)) {
                repository.sweepJunk()
            }
            val sweepDevice = inputData.getBoolean(TeeVRepository.INCLUDE_DEVICE_SWEEP, false)
            val trash = inputData.getBoolean(TeeVRepository.INCLUDE_TRASH, false)
            if (sweepDevice || trash) {
                repository.cleanDeviceStorage(sweep = sweepDevice, trash = trash)
            }
            if (inputData.getBoolean(TeeVRepository.INCLUDE_INSTALLED_APKS, false)) {
                repository.removeInstalledApks()
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CleanupWorker", "Error during scheduled cleanup", e)
            Result.retry()
        }
}
