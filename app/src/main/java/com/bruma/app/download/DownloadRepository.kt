package com.bruma.app.download

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bruma.app.data.AppSettings
import com.bruma.app.data.SettingsRepository
import com.bruma.app.data.db.BrumaDatabase
import com.bruma.app.data.db.DownloadEntity
import com.bruma.app.data.db.DownloadState
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadRepository(
    private val app: Application,
    db: BrumaDatabase,
    private val settings: SettingsRepository,
) {
    val dao = db.downloads()
    val all: Flow<List<DownloadEntity>> = dao.all()
    val gate = ConcurrencyGate()
    private val wm get() = WorkManager.getInstance(app)

    fun workName(id: Long) = "download-$id"

    fun processId(id: Long) = "bruma-$id"

    fun workDir(id: Long) = File(app.noBackupFilesDir, "work/$id")

    suspend fun enqueue(items: List<DownloadEntity>) {
        val s = settings.current()
        for (d in items) {
            val id = dao.insert(d)
            schedule(id, s)
        }
    }

    private fun schedule(
        id: Long,
        s: AppSettings,
    ) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(if (s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(s.pauseLowBattery)
                .build()
        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag("download")
                .build()
        wm.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun stopProcess(id: Long) {
        wm.cancelUniqueWork(workName(id))
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId(id)) }
    }

    suspend fun pause(id: Long) {
        dao.setState(id, DownloadState.PAUSED.name, "En pausa", "USER")
        stopProcess(id)
    }

    suspend fun resume(id: Long) {
        dao.requeue(id)
        schedule(id, settings.current())
    }

    suspend fun retry(id: Long) {
        dao.resetForRetry(id)
        schedule(id, settings.current())
    }

    /** Cancela, borra los archivos parciales y quita la descarga de la lista. */
    suspend fun cancel(id: Long) {
        dao.setState(id, DownloadState.CANCELED.name, null, "USER")
        stopProcess(id)
        delay(500)
        withContext(Dispatchers.IO) { workDir(id).deleteRecursively() }
        dao.delete(id)
    }

    suspend fun remove(id: Long) {
        withContext(Dispatchers.IO) { workDir(id).deleteRecursively() }
        dao.delete(id)
    }

    suspend fun pauseAll() {
        dao.running().forEach { pause(it.id) }
    }

    suspend fun resumeAll() {
        val s = settings.current()
        dao.paused().forEach {
            dao.requeue(it.id)
            schedule(it.id, s)
        }
    }

    suspend fun clearCompleted() = dao.clearCompleted()
}
