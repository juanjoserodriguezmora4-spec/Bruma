package com.bruma.app.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bruma.app.container
import com.bruma.app.data.SettingsRepository
import com.bruma.app.engine.Engine
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Botones "Pausar" y "Cancelar" de la notificación. */
class DownloadActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PAUSE = "com.bruma.app.PAUSE"
        const val ACTION_CANCEL = "com.bruma.app.CANCEL"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = intent.getLongExtra("id", -1L)
        if (id < 0) return
        val pending = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> c.downloads.pause(id)
                    ACTION_CANCEL -> c.downloads.cancel(id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Revisa una vez por semana (solo con Wi-Fi) si hay una versión nueva de yt-dlp. */
class EngineUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            val (_, version) = Engine.update(applicationContext)
            val settings = applicationContext.container.settings
            if (version != null) settings.set(SettingsRepository.ENGINE_VERSION, version)
            settings.set(SettingsRepository.ENGINE_CHECKED_AT, System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }

    companion object {
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<EngineUpdateWorker>(7, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork("engine-update", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
