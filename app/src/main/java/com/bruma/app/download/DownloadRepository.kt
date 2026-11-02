package com.bruma.app.download

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Cola de descargas.
 *
 * Las descargas se guardan como EN_COLA y un "despachador" solo arranca tantas como
 * diga Ajustes → Descargas simultáneas (máximo 5). Cada vez que una termina, se pausa
 * o se cancela, el despachador arranca la siguiente. Así un álbum de 15 canciones
 * se baja de a 5, sin tener 15 trabajos abiertos al tiempo.
 */
class DownloadRepository(
    private val app: Application,
    db: BrumaDatabase,
    private val settings: SettingsRepository,
) {
    val dao = db.downloads()
    val all: Flow<List<DownloadEntity>> = dao.all()
    val gate = ConcurrencyGate()
    private val wm get() = WorkManager.getInstance(app)
    private val dispatchLock = Mutex()

    fun workName(id: Long) = "download-$id"

    fun processId(id: Long) = "bruma-$id"

    fun workDir(id: Long) = File(app.noBackupFilesDir, "work/$id")

    private fun tagOf(id: Long) = "dl-$id"

    suspend fun enqueue(items: List<DownloadEntity>) {
        for (d in items) dao.insert(d)
        dispatch()
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
                .addTag(tagOf(id))
                .build()
        wm.enqueueUniqueWork(workName(id), ExistingWorkPolicy.KEEP, request)
    }

    private fun WorkInfo.isPending() =
        state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING || state == WorkInfo.State.BLOCKED

    /**
     * Arranca las siguientes descargas en cola si hay cupo.
     * [finishing] es la descarga que está terminando en este momento: todavía figura
     * como "en ejecución", así que no se cuenta para dejar libre su cupo.
     */
    suspend fun dispatch(finishing: Long? = null) {
        dispatchLock.withLock {
            withContext(Dispatchers.IO) {
                val s = settings.current()
                val infos = runCatching { wm.getWorkInfosByTag("download").get() }.getOrDefault(emptyList())
                val busy = infos.count { it.isPending() && (finishing == null || tagOf(finishing) !in it.tags) }
                var free = s.maxConcurrent.coerceIn(1, 5) - busy
                if (free <= 0) return@withContext
                for (d in dao.queued()) {
                    if (free <= 0) break
                    if (d.id == finishing) continue
                    val already =
                        runCatching { wm.getWorkInfosForUniqueWork(workName(d.id)).get() }
                            .getOrDefault(emptyList())
                            .any { it.isPending() }
                    if (already) continue
                    schedule(d.id, s)
                    free--
                }
            }
        }
    }

    /** Detiene el trabajo y espera a que WorkManager confirme, para no contarlo como ocupado. */
    private suspend fun stopProcess(id: Long) {
        withContext(Dispatchers.IO) {
            runCatching { wm.cancelUniqueWork(workName(id)).result.get() }
            runCatching { YoutubeDL.getInstance().destroyProcessById(processId(id)) }
        }
    }

    suspend fun pause(id: Long) {
        dao.setState(id, DownloadState.PAUSED.name, "En pausa", "USER")
        stopProcess(id)
        dispatch()
    }

    suspend fun resume(id: Long) {
        // Si estaba esperando red, se reinicia para que arranque ya y no espere el reintento.
        stopProcess(id)
        dao.requeue(id)
        dispatch()
    }

    suspend fun retry(id: Long) {
        stopProcess(id)
        dao.resetForRetry(id)
        dispatch()
    }

    /** Cancela, borra los archivos parciales y quita la descarga de la lista. */
    suspend fun cancel(id: Long) {
        dao.setState(id, DownloadState.CANCELED.name, null, "USER")
        stopProcess(id)
        delay(300)
        withContext(Dispatchers.IO) { workDir(id).deleteRecursively() }
        dao.delete(id)
        dispatch()
    }

    suspend fun remove(id: Long) {
        withContext(Dispatchers.IO) { workDir(id).deleteRecursively() }
        dao.delete(id)
    }

    suspend fun pauseAll() {
        val ids = dao.running().map { it.id } + dao.queued().map { it.id }
        for (id in ids.distinct()) {
            dao.setState(id, DownloadState.PAUSED.name, "En pausa", "USER")
            stopProcess(id)
        }
    }

    suspend fun resumeAll() {
        dao.paused().forEach { dao.requeue(it.id) }
        dispatch()
    }

    suspend fun clearCompleted() = dao.clearCompleted()
}
