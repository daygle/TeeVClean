package com.teevclean.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val cacheDir = applicationContext.cacheDir
            val files = cacheDir.listFiles()
            var allDeleted = true
            
            files?.forEach { file ->
                if (!file.deleteRecursively()) {
                    allDeleted = false
                    Log.w("CleanupWorker", "Failed to delete: ${file.absolutePath}")
                }
            }
            
            if (allDeleted) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e("CleanupWorker", "Error during scheduled cleanup", e)
            Result.failure()
        }
    }
}
