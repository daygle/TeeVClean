package com.teevclean.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        applicationContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        return Result.success()
    }
}
