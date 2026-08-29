package com.teevclean.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val failed = applicationContext.cacheDir.listFiles()
            ?.any { !it.deleteRecursively() && it.exists() }
            ?: false
        return if (failed) Result.retry() else Result.success()
    }
}
