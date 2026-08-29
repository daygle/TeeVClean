package com.teevclean.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        try {
            // Only unattended-safe actions run here — never user files or other apps' caches.
            val repository = TeeVRepository(applicationContext)
            repository.clearOwnCache()
            if (inputData.getBoolean(TeeVRepository.INCLUDE_SWEEP, false)) {
                repository.sweepJunk()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("CleanupWorker", "Error during scheduled cleanup", e)
            Result.retry()
        }
}
