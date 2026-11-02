package com.bruma.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

/** Un bloque de recomendaciones en la pantalla de Inicio. */
data class DiscoverSection(
    val id: String,
    val title: String,
    val subtitle: String?,
    val items: List<SearchResult>,
)

/**
 * Ayudas para la sección "Descubrir":
 * - Limpia nombres de artistas que vienen de YouTube ("Artista - Topic", "ArtistaVEVO").
 * - Averigua el género de un artista en MusicBrainz, una base de datos musical abierta y gratuita.
 */
object Discover {
    /** Géneros que se muestran si todavía no hay música en la biblioteca para deducirlos. */
    val DEFAULT_GENRES =
        listOf(
            "Música popular",
            "Reggaetón",
            "Salsa",
            "Vallenato",
            "Bachata",
            "Pop latino",
            "Rock en español",
            "Electrónica",
            "Cumbia",
            "Regional mexicano",
        )

    /** Etiquetas de MusicBrainz (en inglés) → nombre del género en español. */
    private val TAGS =
        mapOf(
            "reggaeton" to "Reggaetón",
            "latin pop" to "Pop latino",
            "salsa" to "Salsa",
            "vallenato" to "Vallenato",
            "bachata" to "Bachata",
            "cumbia" to "Cumbia",
            "merengue" to "Merengue",
            "latin rock" to "Rock en español",
            "rock en español" to "Rock en español",
            "rock" to "Rock",
            "alternative rock" to "Rock alternativo",
            "pop" to "Pop",
            "electronic" to "Electrónica",
            "edm" to "Electrónica",
            "house" to "Electrónica",
            "techno" to "Electrónica",
            "hip hop" to "Hip hop",
            "rap" to "Rap",
            "trap" to "Trap",
            "latin trap" to "Trap latino",
            "regional mexican" to "Regional mexicano",
            "corridos tumbados" to "Corridos",
            "corrido" to "Corridos",
            "ranchera" to "Ranchera",
            "mariachi" to "Ranchera",
            "banda" to "Banda",
            "bolero" to "Bolero",
            "tango" to "Tango",
            "jazz" to "Jazz",
            "blues" to "Blues",
            "metal" to "Metal",
            "heavy metal" to "Metal",
            "punk" to "Punk",
            "indie" to "Indie",
            "indie pop" to "Indie",
            "r&b" to "R&B",
            "soul" to "Soul",
            "funk" to "Funk",
            "reggae" to "Reggae",
            "k-pop" to "K-pop",
            "classical" to "Clásica",
            "música popular" to "Música popular",
            "música de despecho" to "Música popular",
        )

    private val genreCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    /** Quita sufijos típicos de canales de YouTube para quedarse con el nombre del artista. */
    fun cleanArtist(raw: String): String =
        raw
            .replace(Regex("(?i)\\s*-\\s*topic$"), "")
            .replace(Regex("(?i)vevo$"), "")
            .replace(Regex("(?i)\\s*(oficial|official|music|tv)$"), "")
            .substringBefore(",")
            .substringBefore(" & ")
            .substringBefore(" ft.")
            .substringBefore(" feat.")
            .substringBefore(" x ")
            .trim()

    /** Texto sin tildes ni mayúsculas, para comparar títulos. */
    fun norm(s: String): String =
        Normalizer
            .normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Géneros de un artista según MusicBrainz (puede devolver lista vacía). */
    suspend fun genresOf(artist: String): List<String> =
        withContext(Dispatchers.IO) {
            genreCache[artist.lowercase()]?.let { return@withContext it }
            val q = URLEncoder.encode("artist:\"$artist\"", "UTF-8")
            val conn =
                URL("https://musicbrainz.org/ws/2/artist/?query=$q&fmt=json&limit=1").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                // MusicBrainz pide que cada app se identifique con un nombre y una página.
                conn.setRequestProperty("User-Agent", "Bruma/1.3 ( https://github.com/juanjoserodriguezmora4-spec/Bruma )")
                conn.setRequestProperty("Accept", "application/json")
                if (conn.responseCode !in 200..299) return@withContext emptyList()
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val a = json.optJSONArray("artists")?.optJSONObject(0) ?: return@withContext emptyList()
                if (a.optInt("score", 0) < 85) return@withContext emptyList()
                val tags = a.optJSONArray("tags") ?: return@withContext emptyList()
                val list =
                    (0 until tags.length())
                        .mapNotNull { tags.optJSONObject(it) }
                        .sortedByDescending { it.optInt("count", 0) }
                        .mapNotNull { TAGS[it.optString("name").lowercase()] }
                        .distinct()
                        .take(3)
                genreCache[artist.lowercase()] = list
                list
            } finally {
                conn.disconnect()
            }
        }
}
