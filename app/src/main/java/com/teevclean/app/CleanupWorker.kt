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

        /** Runs one part of the clean-up and remembers it once it has completed. */
        suspend fun part(vararg partKinds: CleanupKind, clean: suspend () -> CleanupResult) {
            results += clean()
            kinds.addAll(partKinds)
        }

        return try {
            part(CleanupKind.OWN_CACHE) { repository.clearOwnCache() }
            if (inputData.getBoolean(TeeVRepository.INCLUDE_SWEEP, false)) {
                part(CleanupKind.SWEEP_FOLDERS) { repository.sweepJunk() }
            }
            val sweepDevice = inputData.getBoolean(TeeVRepository.INCLUDE_DEVICE_SWEEP, false)
            val trash = inputData.getBoolean(TeeVRepository.INCLUDE_TRASH, false)
            if (sweepDevice || trash) {
                val deviceKinds = listOfNotNull(
                    CleanupKind.SWEEP_DEVICE.takeIf { sweepDevice },
                    CleanupKind.TRASH.takeIf { trash },
                )
                part(*deviceKinds.toTypedArray()) { repository.cleanDeviceStorage(sweep = sweepDevice, trash = trash) }
            }
            if (inputData.getBoolean(TeeVRepository.INCLUDE_INSTALLED_APKS, false)) {
                part(CleanupKind.INSTALLERS) { repository.removeInstalledApks() }
            }
            finished = true
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
