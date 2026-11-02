package com.bruma.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Una página de resultados y la "llave" para pedir la siguiente (null si no hay más). */
data class SearchPage(
    val results: List<SearchResult>,
    val next: String?,
)

enum class SearchKind(
    val label: String,
) {
    SONGS("Canciones"),
    ALBUMS("Álbumes y listas"),
}

/**
 * Acceso rápido a YouTube sin pasar por Python.
 *
 * Usa las mismas direcciones internas que la página web de YouTube (youtubei/v1):
 * - search: buscar canciones o álbumes, con páginas infinitas (continuation).
 * - next: el "Mix" automático de un vídeo, que trae canciones parecidas.
 *
 * Si YouTube cambia su formato y algo falla, Engine vuelve a yt-dlp automáticamente.
 */
object YouTubeSearch {
    private const val BASE = "https://www.youtube.com/youtubei/v1/"
    private const val CLIENT_VERSION = "2.20250101.00.00"

    /** Filtros de YouTube: solo vídeos y solo listas de reproducción. */
    private const val ONLY_VIDEOS = "EgIQAQ=="
    private const val ONLY_PLAYLISTS = "EgIQAw=="

    private fun context() =
        JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", CLIENT_VERSION)
                .put("hl", "es")
                .put("gl", "CO"),
        )

    private fun post(
        endpoint: String,
        body: JSONObject,
    ): JSONObject {
        val conn = (URL("$BASE$endpoint?prettyPrint=false").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 6000
            conn.readTimeout = 9000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-YouTube-Client-Name", "1")
            conn.setRequestProperty("X-YouTube-Client-Version", CLIENT_VERSION)
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
            )
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) error("YouTube respondió ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun search(
        query: String,
        kind: SearchKind,
    ): SearchPage =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject()
                    .put("context", context())
                    .put("query", query)
                    .put("params", if (kind == SearchKind.ALBUMS) ONLY_PLAYLISTS else ONLY_VIDEOS)
            page(post("search", body), kind)
        }

    /** Siguiente página de una búsqueda. */
    suspend fun more(
        token: String,
        kind: SearchKind,
    ): SearchPage =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("context", context()).put("continuation", token)
            page(post("search", body), kind)
        }

    /** Mix automático de YouTube a partir de una canción: devuelve canciones parecidas. */
    suspend fun mix(
        videoId: String,
        limit: Int = 25,
    ): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject()
                    .put("context", context())
                    .put("videoId", videoId)
                    .put("playlistId", "RD$videoId")
            val json = post("next", body)
            val found = mutableListOf<SearchResult>()
            collect(json, found, limit * 2, SearchKind.SONGS)
            found.distinctBy { it.url }.filter { idOf(it.url) != videoId }.take(limit)
        }

    private fun page(
        json: JSONObject,
        kind: SearchKind,
    ): SearchPage {
        val found = mutableListOf<SearchResult>()
        collect(json, found, 60, kind)
        return SearchPage(found.distinctBy { it.url }, findToken(json))
    }

    // ------------------------------------------------------------------ lectura del JSON

    /** Recorre todo el JSON buscando tarjetas de vídeo o de lista, sin depender de la ruta exacta. */
    private fun collect(
        node: Any?,
        out: MutableList<SearchResult>,
        max: Int,
        kind: SearchKind,
    ) {
        if (out.size >= max) return
        when (node) {
            is JSONObject -> {
                for (key in listOf("videoRenderer", "playlistPanelVideoRenderer", "compactVideoRenderer")) {
                    val v = node.optJSONObject(key) ?: continue
                    if (kind == SearchKind.SONGS) video(v)?.let { out += it }
                    return
                }
                node.optJSONObject("playlistRenderer")?.let { p ->
                    if (kind == SearchKind.ALBUMS) playlist(p)?.let { out += it }
                    return
                }
                node.optJSONObject("lockupViewModel")?.let { l ->
                    lockup(l, kind)?.let { out += it }
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) collect(node.opt(keys.next()), out, max, kind)
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out, max, kind)
        }
    }

    private fun findToken(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node
                    .optJSONObject("continuationCommand")
                    ?.optString("token")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) findToken(node.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findToken(node.opt(i))?.let { return it }
        }
        return null
    }

    /** Primer texto que cumpla una condición, buscando en todo el subárbol. */
    private fun findString(
        node: Any?,
        key: String,
        accept: (String) -> Boolean = { true },
    ): String? {
        when (node) {
            is JSONObject -> {
                val v = node.opt(key)
                if (v is String && v.isNotBlank() && accept(v)) return v
                val keys = node.keys()
                while (keys.hasNext()) findString(node.opt(keys.next()), key, accept)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findString(node.opt(i), key, accept)?.let { return it }
        }
        return null
    }

    private fun JSONObject.text(key: String): String? {
        val o = optJSONObject(key) ?: return null
        o.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return null
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }.ifBlank { null }
    }

    private fun parseDuration(s: String?): Int {
        if (s.isNullOrBlank() || !s.contains(":")) return 0
        return s.split(":").mapNotNull { it.trim().toIntOrNull() }.fold(0) { acc, n -> acc * 60 + n }
    }

    private fun video(v: JSONObject): SearchResult? {
        val id = v.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        val length = v.text("lengthText")
        val views = v.text("viewCountText")?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
        return SearchResult(
            url = "https://www.youtube.com/watch?v=$id",
            title = v.text("title") ?: return null,
            author = v.text("ownerText") ?: v.text("shortBylineText") ?: v.text("longBylineText"),
            durationSec = parseDuration(length),
            thumbnail = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
            views = views,
            isLive = length == null && v.toString().contains("LIVE_NOW"),
        )
    }

    private fun playlist(p: JSONObject): SearchResult? {
        val id = p.optString("playlistId").takeIf { it.isNotBlank() } ?: return null
        val thumb =
            findString(p.optJSONArray("thumbnails") ?: p.optJSONObject("thumbnail"), "url")
        return SearchResult(
            url = "https://www.youtube.com/playlist?list=$id",
            title = p.text("title") ?: return null,
            author = p.text("shortBylineText") ?: p.text("longBylineText"),
            durationSec = 0,
            thumbnail = thumb,
            views = 0,
            isLive = false,
            isPlaylist = true,
            itemCount = p.optString("videoCount").filter { it.isDigit() }.toIntOrNull() ?: 0,
        )
    }

    /** Formato nuevo de YouTube (2024-2025) para tarjetas de listas y algunos vídeos. */
    private fun lockup(
        l: JSONObject,
        kind: SearchKind,
    ): SearchResult? {
        val id = l.optString("contentId").takeIf { it.isNotBlank() } ?: return null
        val type = l.optString("contentType")
        val meta = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = meta?.optJSONObject("title")?.optString("content")?.takeIf { it.isNotBlank() } ?: return null
        val author = findString(meta?.optJSONObject("metadata"), "content")
        val image = l.optJSONObject("contentImage")
        return if (type.contains("PLAYLIST") || type.contains("ALBUM") || type.contains("PODCAST")) {
            if (kind != SearchKind.ALBUMS) return null
            val count = findString(image, "text") { t -> t.any { it.isDigit() } }
            SearchResult(
                url = "https://www.youtube.com/playlist?list=$id",
                title = title,
                author = author,
                durationSec = 0,
                thumbnail = findString(image, "url"),
                views = 0,
                isLive = false,
                isPlaylist = true,
                itemCount = count?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
            )
        } else if (type.contains("VIDEO")) {
            if (kind != SearchKind.SONGS) return null
            val badge = findString(image, "text") { it.contains(":") }
            SearchResult(
                url = "https://www.youtube.com/watch?v=$id",
                title = title,
                author = author,
                durationSec = parseDuration(badge),
                thumbnail = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
                views = 0,
                isLive = false,
            )
        } else {
            null
        }
    }

    private val idRegex = Regex("""(?:v=|youtu\.be/|/shorts/)([\w-]{11})""")

    /** Saca el identificador de 11 letras de un enlace de YouTube. */
    fun idOf(url: String?): String? = url?.let { idRegex.find(it)?.groupValues?.get(1) }
}
