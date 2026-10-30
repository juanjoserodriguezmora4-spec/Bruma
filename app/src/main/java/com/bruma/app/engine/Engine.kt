package com.bruma.app.engine

import android.content.Context
import com.bruma.app.data.AppSettings
import com.bruma.app.data.db.DownloadEntity
import com.bruma.app.data.db.isVideo
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Una entrada de una lista de reproducción o álbum. */
data class PlaylistEntry(
    val url: String,
    val title: String,
    val durationSec: Int,
)

/** Lo que devuelve el motor al analizar un enlace. */
data class MediaInfo(
    val url: String,
    val title: String,
    val author: String?,
    val platform: String?,
    val durationSec: Int,
    val thumbnail: String?,
    val entries: List<PlaylistEntry>,
    /** Calidades de vídeo disponibles (1080, 720, 480, 360). 0 = original sin altura conocida. */
    val videoQualities: List<Int>,
    /** Tamaño estimado en bytes para cada calidad de vídeo. */
    val videoSizes: Map<Int, Long>,
    val hasVideo: Boolean,
    val sourceAudioKbps: Int,
) {
    val isPlaylist: Boolean get() = entries.isNotEmpty()
}

enum class SearchPlatform(
    val label: String,
) {
    YOUTUBE("YouTube"),
    SOUNDCLOUD("SoundCloud"),
}

/** Un resultado de búsqueda. */
data class SearchResult(
    val url: String,
    val title: String,
    val author: String?,
    val durationSec: Int,
    val thumbnail: String?,
    val views: Long,
    val isLive: Boolean,
)

enum class ErrorType(
    val retryable: Boolean,
) {
    NETWORK(true),
    RATE_LIMIT(true),
    UNKNOWN(true),
    UNSUPPORTED(false),
    UNAVAILABLE(false),
    DRM(false),
    NO_SPACE(false),
    STORAGE(false),
    PROCESSING(false),
    INVALID_URL(false),
}

object Engine {
    private val mutex = Mutex()

    @Volatile private var ready = false

    /** Prepara Python, yt-dlp y FFmpeg. La primera vez tarda unos segundos. */
    suspend fun ensureInit(context: Context) {
        if (ready) return
        mutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
            }
            ready = true
        }
    }

    suspend fun analyze(
        context: Context,
        url: String,
    ): MediaInfo =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            val req = YoutubeDLRequest(url)
            req.addOption("-J")
            req.addOption("--flat-playlist")
            req.addOption("--no-playlist")
            req.addOption("--no-warnings")
            val response = YoutubeDL.getInstance().execute(req, null, null)
            parse(JSONObject(response.out), url)
        }

    suspend fun update(context: Context): Pair<String, String?> =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
            val version = YoutubeDL.getInstance().version(context.applicationContext)
            Pair(status?.name ?: "UNKNOWN", version)
        }

    fun version(context: Context): String? = runCatching { YoutubeDL.getInstance().version(context.applicationContext) }.getOrNull()

    // ---------------------------------------------------------------- búsqueda

    /**
     * Busca por texto en YouTube ("ytsearch") o SoundCloud ("scsearch").
     * Usa --flat-playlist para traer solo la lista de resultados, sin analizar cada vídeo.
     */
    suspend fun search(
        context: Context,
        query: String,
        platform: SearchPlatform,
        limit: Int = 20,
    ): List<SearchResult> =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            val prefix = if (platform == SearchPlatform.SOUNDCLOUD) "scsearch" else "ytsearch"
            val req = YoutubeDLRequest("$prefix$limit:${query.trim()}")
            req.addOption("-J")
            req.addOption("--flat-playlist")
            req.addOption("--no-warnings")
            val response = YoutubeDL.getInstance().execute(req, null, null)
            parseSearch(JSONObject(response.out), platform)
        }

    fun parseSearch(
        o: JSONObject,
        platform: SearchPlatform,
    ): List<SearchResult> {
        val arr = o.optJSONArray("entries") ?: return emptyList()
        val out = mutableListOf<SearchResult>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val id = e.str("id")
            // SoundCloud trae la dirección pública en webpage_url; YouTube la trae en url.
            val url =
                e.str("webpage_url")
                    ?: e.str("url")?.takeIf { it.startsWith("http") }
                    ?: if (platform == SearchPlatform.YOUTUBE && id != null) "https://www.youtube.com/watch?v=$id" else null
            if (url == null) continue
            val thumbs = e.optJSONArray("thumbnails")
            val thumb =
                e.str("thumbnail")
                    ?: thumbs?.let { t ->
                        // La miniatura de tamaño medio carga rápido y se ve bien en la lista.
                        (0 until t.length()).mapNotNull { t.optJSONObject(it)?.str("url") }.let { urls ->
                            urls.getOrNull(urls.size / 2) ?: urls.lastOrNull()
                        }
                    }
                    ?: if (platform == SearchPlatform.YOUTUBE && id != null) "https://i.ytimg.com/vi/$id/mqdefault.jpg" else null
            out +=
                SearchResult(
                    url = url,
                    title = e.str("title") ?: url,
                    author = e.str("channel") ?: e.str("uploader") ?: e.str("artist"),
                    durationSec = e.num("duration")?.toInt() ?: 0,
                    thumbnail = thumb,
                    views = e.num("view_count")?.toLong() ?: 0L,
                    isLive = e.str("live_status") == "is_live",
                )
        }
        // Sin repetidos: la lista de resultados usa el enlace como identificador.
        return out.distinctBy { it.url }
    }

    // ---------------------------------------------------------------- análisis

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() && it != "none" && it != "null" } else null

    private fun JSONObject.num(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private data class Fmt(
        val height: Int,
        val tbr: Double,
        val abr: Double,
        val size: Long?,
        val hasV: Boolean,
        val hasA: Boolean,
    )

    private val audioExt = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")

    fun parse(
        o: JSONObject,
        url: String,
    ): MediaInfo {
        val title = o.str("title") ?: url
        val author = o.str("uploader") ?: o.str("channel") ?: o.str("artist") ?: o.str("playlist_uploader")
        val platform = o.str("extractor_key") ?: o.str("ie_key")
        val thumb =
            o.str("thumbnail") ?: o.optJSONArray("thumbnails")?.let { arr ->
                if (arr.length() > 0) arr.optJSONObject(arr.length() - 1)?.str("url") else null
            }

        if (o.str("_type") == "playlist") {
            val entries = mutableListOf<PlaylistEntry>()
            val arr = o.optJSONArray("entries")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val u = e.str("url") ?: e.str("webpage_url") ?: continue
                    entries += PlaylistEntry(u, e.str("title") ?: u, e.num("duration")?.toInt() ?: 0)
                }
            }
            return MediaInfo(
                url,
                title,
                author,
                platform,
                entries.sumOf { it.durationSec },
                thumb,
                entries,
                listOf(1080, 720, 480, 360),
                emptyMap(),
                hasVideo = true,
                sourceAudioKbps = 0,
            )
        }

        val duration = o.num("duration")?.toInt() ?: 0
        val fmts = mutableListOf<Fmt>()
        val arr = o.optJSONArray("formats")
        val list = if (arr != null && arr.length() > 0) (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } else listOf(o)

        fun JSONObject.raw(key: String): String = if (has(key) && !isNull(key)) optString(key, "") else ""
        for (f in list) {
            val v = f.raw("vcodec")
            val a = f.raw("acodec")
            val ext = f.raw("ext").lowercase()
            if (ext == "mhtml") continue
            val hasV =
                when {
                    v == "none" -> false
                    v.isNotBlank() -> true
                    else -> ext.isNotBlank() && ext !in audioExt
                }
            val hasA = a != "none"
            val size = (f.num("filesize") ?: f.num("filesize_approx"))?.toLong()
            fmts += Fmt(f.num("height")?.toInt() ?: 0, f.num("tbr") ?: 0.0, f.num("abr") ?: 0.0, size, hasV, hasA)
        }

        val videos = fmts.filter { it.hasV }
        val audiosOnly = fmts.filter { it.hasA && !it.hasV }
        val bestAudio = audiosOnly.maxByOrNull { it.abr.takeIf { a -> a > 0 } ?: it.tbr }
        val maxHeight = videos.maxOfOrNull { it.height } ?: 0
        val qualities =
            when {
                videos.isEmpty() -> emptyList()
                maxHeight <= 0 -> listOf(0)
                else -> listOf(1080, 720, 480, 360).filter { maxHeight >= it * 0.9 }.ifEmpty { listOf(0) }
            }

        fun est(
            f: Fmt?,
            kbps: Double,
        ): Long = f?.size ?: (kbps * duration * 125).toLong()

        val sizes =
            qualities.associateWith { q ->
                val v =
                    videos.filter { q == 0 || it.height <= q }.maxByOrNull { it.height * 100000 + it.tbr.toInt() }
                        ?: videos.minByOrNull { it.height }
                val vs = est(v, v?.tbr ?: 0.0)
                val needsAudio = v != null && !v.hasA
                vs + if (needsAudio) est(bestAudio, bestAudio?.abr ?: 128.0) else 0L
            }

        val srcKbps = (bestAudio?.abr ?: fmts.filter { it.hasA }.maxOfOrNull { it.abr } ?: 0.0).toInt()
        return MediaInfo(url, title, author, platform, duration, thumb, emptyList(), qualities, sizes, videos.isNotEmpty(), srcKbps)
    }

    // --------------------------------------------------------- descarga

    /** Arma la orden de yt-dlp según el formato y la calidad elegidos. */
    fun buildRequest(
        d: DownloadEntity,
        dir: File,
        s: AppSettings,
    ): YoutubeDLRequest {
        val req = YoutubeDLRequest(d.url)
        req.addOption("-o", File(dir, "%(title).120B [%(id)s].%(ext)s").absolutePath)
        req.addOption("--no-playlist")
        req.addOption("--no-mtime")
        req.addOption("--write-info-json")
        req.addOption("--retries", "3")
        req.addOption("--fragment-retries", "5")
        req.addOption("-N", "4")

        if (d.isVideo) {
            val sort =
                buildString {
                    if (d.quality > 0) append("res:${d.quality},")
                    append(if (d.container == "WEBM") "vcodec:vp9,acodec:opus" else "vcodec:h264,acodec:m4a")
                }
            req.addOption("-S", sort)
            req.addOption("-f", "bv*+ba/b")
            req.addOption("--merge-output-format", if (d.container == "WEBM") "webm" else "mp4")
        } else {
            req.addOption("-f", "ba/b")
            req.addOption("-x")
            req.addOption("--audio-format", d.container.lowercase())
            if (d.container != "FLAC" && d.quality > 0) req.addOption("--audio-quality", "${d.quality}K")
        }

        if (s.embedMetadata) {
            // "Artista - Canción (Video Oficial)"  →  artista + título limpio
            req.addOption("--parse-metadata", "title:^(?P<artist>.+?)\\s+[-–]\\s+(?P<title>.+)$")
            req.addOption(
                "--parse-metadata",
                "title:(?i)^(?P<title>.+?)\\s*[(\\[](?:official|video|lyric|lyrics|audio|hd|4k|visualizer|oficial|letra)[^)\\]]*[)\\]]\\s*$",
            )
            req.addOption("--embed-metadata")
            req.addOption("--write-thumbnail")
            req.addOption("--convert-thumbnails", "jpg")
            if (!(d.isVideo && d.container == "WEBM")) req.addOption("--embed-thumbnail")
            if (!d.isVideo && s.cropCover) {
                // Recorta la miniatura (16:9) a un cuadrado centrado para usarla como carátula.
                req.addOption(
                    "--ppa",
                    "ThumbnailsConvertor+FFmpeg_o:-c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"",
                )
            }
        }
        return req
    }

    // --------------------------------------------------------- errores

    fun classify(message: String?): ErrorType {
        val m = (message ?: "").lowercase()
        return when {
            m.contains("no space left") || m.contains("enospc") -> ErrorType.NO_SPACE
            m.contains("drm") -> ErrorType.DRM
            m.contains("unsupported url") || m.contains("is not a valid url") || m.contains("no video formats") -> ErrorType.UNSUPPORTED
            m.contains("http error 429") || m.contains("too many requests") -> ErrorType.RATE_LIMIT
            m.contains("private video") ||
                m.contains("sign in") ||
                m.contains("not available") ||
                m.contains("members-only") ||
                m.contains("age-restricted") ||
                m.contains("confirm your age") ||
                m.contains("has been removed") ||
                m.contains("http error 404") ||
                m.contains("video unavailable") -> ErrorType.UNAVAILABLE
            m.contains("unable to download") ||
                m.contains("timed out") ||
                m.contains("connection") ||
                m.contains("network is unreachable") ||
                m.contains("name resolution") ||
                m.contains("errno 101") ||
                m.contains("errno 104") ||
                m.contains("errno 110") ||
                m.contains("incompleteread") ||
                m.contains("ssl") ||
                m.contains("http error 5") -> ErrorType.NETWORK
            m.contains("ffmpeg") || m.contains("postprocessing") || m.contains("conversion failed") -> ErrorType.PROCESSING
            m.contains("permission denied") || m.contains("eacces") -> ErrorType.STORAGE
            else -> ErrorType.UNKNOWN
        }
    }

    fun userMessage(t: ErrorType): String =
        when (t) {
            ErrorType.NETWORK -> "Se perdió la conexión"
            ErrorType.RATE_LIMIT -> "El sitio pidió esperar un momento"
            ErrorType.UNSUPPORTED -> "No pudimos leer este enlace. Prueba actualizar el motor en Ajustes"
            ErrorType.UNAVAILABLE -> "Este contenido es privado o no está disponible en tu región"
            ErrorType.DRM -> "Este contenido está protegido y no se puede descargar"
            ErrorType.NO_SPACE -> "Te quedaste sin espacio en el teléfono"
            ErrorType.STORAGE -> "No se puede guardar en esa carpeta"
            ErrorType.PROCESSING -> "Falló la conversión del archivo"
            ErrorType.INVALID_URL -> "Esto no parece un enlace"
            ErrorType.UNKNOWN -> "Algo salió mal con esta descarga"
        }
}
