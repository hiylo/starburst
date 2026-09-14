/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SyncWorker.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.hiylo.starburst.data.repository.SyncRepository
import org.hiylo.starburst.data.repository.SyncBackend
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java,
        ).syncRepository()
        val state = repository.state.first()
        if (!state.config.autoSync || state.config.primaryBackend == org.hiylo.starburst.data.repository.SyncBackend.NONE) {
            return Result.success()
        }
        return runCatching { repository.syncNow() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { if (it is SyncPermissionException) Result.success() else Result.retry() },
            )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncRepository(): SyncRepository
}

object SyncWorkScheduler {
    private const val WORK_NAME = "settings_sync"

    fun update(context: Context, enabled: Boolean, backend: SyncBackend = SyncBackend.NONE) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(requiredNetworkType(backend)).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

internal fun requiredNetworkType(backend: SyncBackend): NetworkType =
    if (backend == SyncBackend.DOCUMENT) NetworkType.NOT_REQUIRED else NetworkType.CONNECTED
