package com.bruma.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Búsqueda rápida en YouTube sin pasar por Python.
 *
 * Hace una sola petición a la misma dirección interna que usa la página web de YouTube
 * (youtubei/v1/search) y lee los resultados del JSON. Tarda menos de un segundo,
 * mientras que yt-dlp necesita arrancar Python cada vez.
 *
 * Si YouTube cambia su formato y esto deja de funcionar, Engine.search vuelve
 * automáticamente a yt-dlp, así que la búsqueda nunca se rompe del todo.
 */
object YouTubeSearch {
    private const val ENDPOINT = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val CLIENT_VERSION = "2.20250101.00.00"

    /** Filtro "solo vídeos" de YouTube (sin canales ni listas). */
    private const val ONLY_VIDEOS = "EgIQAQ=="

    suspend fun search(
        query: String,
        limit: Int,
    ): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject()
                    .put(
                        "context",
                        JSONObject().put(
                            "client",
                            JSONObject()
                                .put("clientName", "WEB")
                                .put("clientVersion", CLIENT_VERSION)
                                .put("hl", "es")
                                .put("gl", "CO"),
                        ),
                    ).put("query", query)
                    .put("params", ONLY_VIDEOS)
                    .toString()

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection)
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-YouTube-Client-Name", "1")
                conn.setRequestProperty("X-YouTube-Client-Version", CLIENT_VERSION)
                conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
                )
                conn.outputStream.use { it.write(body.toByteArray()) }
                if (conn.responseCode !in 200..299) error("YouTube respondió ${conn.responseCode}")
                val text = conn.inputStream.bufferedReader().use { it.readText() }

                val renderers = mutableListOf<JSONObject>()
                collect(JSONObject(text), renderers, limit)
                renderers.mapNotNull { toResult(it) }.distinctBy { it.url }.take(limit)
            } finally {
                conn.disconnect()
            }
        }

    /** Recorre todo el JSON buscando objetos "videoRenderer", sin depender de la ruta exacta. */
    private fun collect(
        node: Any?,
        out: MutableList<JSONObject>,
        limit: Int,
    ) {
        if (out.size >= limit * 2) return
        when (node) {
            is JSONObject -> {
                val vr = node.optJSONObject("videoRenderer")
                if (vr != null) {
                    out += vr
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) collect(node.opt(keys.next()), out, limit)
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out, limit)
        }
    }

    private fun JSONObject.text(key: String): String? {
        val o = optJSONObject(key) ?: return null
        o.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return null
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }.ifBlank { null }
    }

    private fun parseDuration(s: String?): Int {
        if (s.isNullOrBlank()) return 0
        return s.split(":").mapNotNull { it.trim().toIntOrNull() }.fold(0) { acc, n -> acc * 60 + n }
    }

    private fun toResult(v: JSONObject): SearchResult? {
        val id = v.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        val length = v.text("lengthText")
        val views = v.text("viewCountText")?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
        return SearchResult(
            url = "https://www.youtube.com/watch?v=$id",
            title = v.text("title") ?: return null,
            author = v.text("ownerText") ?: v.text("longBylineText"),
            durationSec = parseDuration(length),
            thumbnail = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
            views = views,
            isLive = length == null && v.toString().contains("LIVE"),
        )
    }
}
