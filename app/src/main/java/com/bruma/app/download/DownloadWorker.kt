package com.bruma.app.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bruma.app.container
import com.bruma.app.data.db.DownloadEntity
import com.bruma.app.data.db.DownloadState
import com.bruma.app.data.db.TrackEntity
import com.bruma.app.data.db.isVideo
import com.bruma.app.data.db.st
import com.bruma.app.engine.Engine
import com.bruma.app.engine.ErrorType
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.random.Random

/** Progreso leído de la salida de yt-dlp. */
private data class Prog(
    val state: DownloadState,
    val fraction: Float,
    val total: Long,
    val speed: Long,
    val eta: Long,
    val label: String,
)

private sealed class Outcome {
    data object Success : Outcome()

    data class Failure(
        val type: ErrorType,
        val detail: String?,
    ) : Outcome()
}

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_ID = "id"
        private const val MAX_ATTEMPTS = 5
        private val sizeRe = Regex("""of\s+~?\s*([\d.]+)\s*([KMGT]?i?B)""")
        private val speedRe = Regex("""at\s+([\d.]+)\s*([KMGT]?i?B)/s""")
        private val postTags =
            listOf(
                "[Merger]",
                "[ExtractAudio]",
                "[EmbedThumbnail]",
                "[Metadata]",
                "[FixupM3u8]",
                "[VideoRemuxer]",
                "[ThumbnailsConvertor]",
                "[VideoConvertor]",
                "[MetadataParser]",
            )

        private fun toBytes(
            value: String,
            unit: String,
        ): Long {
            val v = value.toDoubleOrNull() ?: return 0
            val mult =
                when (unit.uppercase()) {
                    "KIB" -> 1024.0
                    "MIB" -> 1024.0 * 1024
                    "GIB" -> 1024.0 * 1024 * 1024
                    "TIB" -> 1024.0 * 1024 * 1024 * 1024
                    "KB" -> 1e3
                    "MB" -> 1e6
                    "GB" -> 1e9
                    "TB" -> 1e12
                    else -> 1.0
                }
            return (v * mult).toLong()
        }
    }

    private val c get() = applicationContext.container
    private val repo get() = c.downloads
    private val dao get() = repo.dao

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L)
        if (id < 0) return Result.failure()
        val initial = dao.get(id) ?: return Result.failure()
        if (initial.st == DownloadState.COMPLETED || initial.st == DownloadState.CANCELED) return Result.success()

        dao.setState(id, DownloadState.QUEUED.name, "En cola", null)
        safeForeground(initial, 0f, "En cola")

        try {
            repo.gate.acquire { c.settings.current().maxConcurrent }
        } catch (e: CancellationException) {
            onStopped(id)
            throw e
        }
        try {
            return run(id)
        } catch (e: CancellationException) {
            onStopped(id)
            throw e
        } finally {
            withContext(NonCancellable) { repo.gate.release() }
        }
    }

    /** El sistema detuvo el trabajo (sin red, sin Wi-Fi, batería baja...) o el usuario lo pausó. */
    private suspend fun onStopped(id: Long) =
        withContext(NonCancellable) {
            runCatching { YoutubeDL.getInstance().destroyProcessById(repo.processId(id)) }
            val d = dao.get(id) ?: return@withContext
            if (d.st == DownloadState.QUEUED ||
                d.st == DownloadState.DOWNLOADING ||
                d.st == DownloadState.PROCESSING ||
                d.st == DownloadState.ANALYZING
            ) {
                dao.setState(id, DownloadState.PAUSED.name, "En pausa · esperando red o batería", "SYSTEM")
            }
        }

    private suspend fun safeForeground(
        d: DownloadEntity,
        fraction: Float,
        label: String,
    ) {
        runCatching { setForeground(Notifications.foregroundInfo(applicationContext, d, fraction, label)) }
    }

    private fun stoppedByUser(d: DownloadEntity?): Boolean =
        d == null || d.st == DownloadState.CANCELED || (d.st == DownloadState.PAUSED && d.errorType == "USER")

    private suspend fun run(id: Long): Result {
        Engine.ensureInit(applicationContext)
        while (true) {
            val d = dao.get(id) ?: return Result.success()
            if (stoppedByUser(d)) return Result.success()
            dao.progress(id, DownloadState.DOWNLOADING.name, d.progress, d.totalBytes, 0, -1, "Conectando…")

            when (val outcome = attempt(d)) {
                is Outcome.Success -> return finish(d)
                is Outcome.Failure -> {
                    val now = dao.get(id)
                    if (stoppedByUser(now)) return Result.success()
                    val attempts = (now?.attempts ?: 0) + 1
                    val type = outcome.type
                    val msg = Engine.userMessage(type)
                    if (!type.retryable || attempts >= MAX_ATTEMPTS) {
                        dao.problem(id, DownloadState.FAILED.name, msg, type.name, attempts)
                        if (type == ErrorType.NO_SPACE) {
                            Notifications.problem(applicationContext, id, "Sin espacio", "Libera espacio para continuar con «${d.title}».")
                        }
                        return Result.failure()
                    }
                    if (!Net.isOnline(applicationContext)) {
                        dao.problem(id, DownloadState.PAUSED.name, "Sin conexión · continuará cuando vuelva la red", type.name, attempts)
                        return Result.retry()
                    }
                    // Espera exponencial con variación aleatoria: 2, 4, 8, 16 s (+/- 20 %)
                    val base = if (type == ErrorType.RATE_LIMIT) 30_000L else 2_000L shl (attempts - 1)
                    val wait = (base * (0.8 + Random.nextDouble() * 0.4)).toLong()
                    dao.problem(id, DownloadState.PAUSED.name, "$msg · reintento en ${wait / 1000} s", type.name, attempts)
                    delay(wait)
                }
            }
        }
    }

    private suspend fun attempt(d: DownloadEntity): Outcome =
        coroutineScope {
            val dir = repo.workDir(d.id).apply { mkdirs() }
            val s = c.settings.current()
            val request = Engine.buildRequest(d, dir, s)
            val pid = repo.processId(d.id)
            val progress = MutableStateFlow<Prog?>(null)

            val reporter =
                launch {
                    progress.filterNotNull().conflate().collect { p ->
                        dao.progress(d.id, p.state.name, p.fraction, p.total, p.speed, p.eta, null)
                        safeForeground(d, p.fraction, p.label)
                        delay(500)
                    }
                }

            val callback: (Float, Long, String) -> Unit = { pct, eta, line ->
                val prev = progress.value
                val isPost = postTags.any { line.startsWith(it) }
                if (isPost) {
                    progress.value = Prog(DownloadState.PROCESSING, 1f, prev?.total ?: 0L, 0L, -1L, "Procesando…")
                } else if (pct >= 0f) {
                    val total = sizeRe.find(line)?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: (prev?.total ?: 0L)
                    val speed = speedRe.find(line)?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: (prev?.speed ?: 0L)
                    val label = "${pct.toInt()} %" + if (eta > 0) " · quedan $eta s" else ""
                    progress.value = Prog(DownloadState.DOWNLOADING, (pct / 100f).coerceIn(0f, 1f), total, speed, eta, label)
                }
            }

            try {
                runInterruptible(Dispatchers.IO) {
                    YoutubeDL.getInstance().execute(request, pid, callback)
                }
                Outcome.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Outcome.Failure(Engine.classify(e.message ?: e.toString()), e.message)
            } finally {
                reporter.cancel()
                runCatching { YoutubeDL.getInstance().destroyProcessById(pid) }
            }
        }

    private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    /** Mueve el archivo terminado a la biblioteca pública y lo registra. */
    private suspend fun finish(d: DownloadEntity): Result {
        dao.progress(d.id, DownloadState.PROCESSING.name, 1f, d.totalBytes, 0, -1, "Guardando en la biblioteca…")
        val s = c.settings.current()
        val dir = repo.workDir(d.id)
        return try {
            withContext(Dispatchers.IO) {
                val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
                val skip = setOf("json", "jpg", "jpeg", "png", "webp", "part", "ytdl", "tmp", "temp")
                var media =
                    files
                        .filter { it.extension.lowercase() !in skip && !it.name.contains(".part") }
                        .maxByOrNull { it.length() } ?: throw IOException("No se encontró el archivo descargado")
                if (d.container == "M4V" && media.extension.equals("mp4", true)) {
                    val renamed = File(media.parentFile, media.nameWithoutExtension + ".m4v")
                    if (media.renameTo(renamed)) media = renamed
                }
                val info =
                    files
                        .firstOrNull { it.name.endsWith(".info.json") }
                        ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
                val thumb =
                    files
                        .filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                        .maxByOrNull { it.length() }

                val title = info?.str("track") ?: info?.str("title") ?: d.title
                val artist = info?.str("artist") ?: info?.str("uploader") ?: info?.str("channel") ?: d.author ?: ""
                val duration = info?.optDouble("duration", 0.0)?.takeIf { !it.isNaN() }?.toInt() ?: 0

                val uri = MediaSaver.save(applicationContext, media, d.isVideo, s.folder)
                val cover =
                    thumb?.let {
                        val dest = File(applicationContext.filesDir, "covers/${System.currentTimeMillis()}_${d.id}.jpg")
                        dest.parentFile?.mkdirs()
                        it.copyTo(dest, overwrite = true)
                        dest.absolutePath
                    }
                val label =
                    when {
                        d.isVideo -> if (d.quality > 0) "${d.container} · ${d.quality}p" else d.container
                        d.container == "FLAC" -> "FLAC · sin pérdida"
                        d.quality > 0 -> "${d.container} · ${d.quality} kbps"
                        else -> d.container
                    }
                val size = media.length()
                val trackId =
                    c.db.tracks().insert(
                        TrackEntity(
                            title = title,
                            artist = artist,
                            durationSec = duration,
                            uri = uri.toString(),
                            mime = Mime.of(media.extension),
                            isVideo = d.isVideo,
                            coverPath = cover,
                            source = d.platform,
                            qualityLabel = label,
                            sizeBytes = size,
                        ),
                    )
                d.targetPlaylistId?.let { c.library.addToPlaylist(it, trackId) }
                dao.complete(d.id, trackId, title, size)
                dir.deleteRecursively()
                Notifications.done(applicationContext, d.id, title)
            }
            Result.success()
        } catch (e: Exception) {
            val type = if ((e.message ?: "").contains("space", true)) ErrorType.NO_SPACE else ErrorType.STORAGE
            dao.problem(d.id, DownloadState.FAILED.name, Engine.userMessage(type), type.name, d.attempts + 1)
            Result.failure()
        }
    }
}
