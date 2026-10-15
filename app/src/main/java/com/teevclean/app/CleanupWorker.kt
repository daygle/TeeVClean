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
    override suspend fun doWork(): Result {
        // Only unattended-safe actions run here - never user files or other apps' caches.
        val repository = TeeVRepository(applicationContext)
        val kinds = mutableListOf<CleanupKind>()
        val results = mutableListOf<CleanupResult>()
        var finished = false
        val options = ScheduleOptions(
            sweepFolders = inputData.getBoolean(TeeVRepository.INCLUDE_SWEEP, false),
            sweepDevice = inputData.getBoolean(TeeVRepository.INCLUDE_DEVICE_SWEEP, false),
            installedApks = inputData.getBoolean(TeeVRepository.INCLUDE_INSTALLED_APKS, false),
            trash = inputData.getBoolean(TeeVRepository.INCLUDE_TRASH, false),
        )

        return try {
            repository.runCleanup(options, kinds, results)
            finished = true
            // Keep the schedule on the chosen time however late this run started.
            repository.scheduleNextRun()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CleanupWorker", "Error during scheduled cleanup", e)
            Result.retry()
        } finally {
            // One history entry per run, also when nothing was found (so the log shows the
            // schedule runs) and when it failed or was stopped (recording the parts that finished).
            repository.recordCleanup(
                kinds,
                CleanupResult(results.sumOf { it.freedBytes }, results.sumOf { it.itemsRemoved }),
                automatic = true,
                complete = finished,
            )
        }
    }
}
