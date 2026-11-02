package com.bruma.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bruma.app.container
import com.bruma.app.data.AppSettings
import com.bruma.app.data.db.DownloadEntity
import com.bruma.app.data.db.DownloadState
import com.bruma.app.data.db.TrackEntity
import com.bruma.app.data.db.st
import com.bruma.app.engine.Discover
import com.bruma.app.engine.DiscoverSection
import com.bruma.app.engine.Engine
import com.bruma.app.engine.ErrorType
import com.bruma.app.engine.MediaInfo
import com.bruma.app.engine.PlaylistEntry
import com.bruma.app.engine.SearchKind
import com.bruma.app.engine.SearchPlatform
import com.bruma.app.engine.SearchResult
import com.bruma.app.engine.YouTubeSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUi(
    val text: String = "",
    val analyzing: Boolean = false,
    val error: String? = null,
    val info: MediaInfo? = null,
    val batch: List<String> = emptyList(),
    val clip: String? = null,
    val clipInfo: MediaInfo? = null,
    /** Texto de la última búsqueda; null cuando no se está mostrando una búsqueda. */
    val searchQuery: String? = null,
    val platform: SearchPlatform = SearchPlatform.YOUTUBE,
    val searching: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val searchError: String? = null,
    /** Enlace del resultado que se está analizando, para mostrar el indicador en esa tarjeta. */
    val openingUrl: String? = null,
    /** La hoja de formatos ya está abierta y los tamaños reales se están cargando. */
    val loadingFormats: Boolean = false,
    val formatsError: String? = null,
    /** Canciones o álbumes, en YouTube. */
    val kind: SearchKind = SearchKind.SONGS,
    /** Llave para pedir la siguiente página de resultados (null = no hay más). */
    val nextToken: String? = null,
    val loadingMore: Boolean = false,
    /** Modo "seleccionar varias" en los resultados. */
    val selecting: Boolean = false,
    val selected: Set<String> = emptySet(),
    /** Canciones elegidas para descargar juntas (desde la búsqueda o las recomendaciones). */
    val batchItems: List<SearchResult> = emptyList(),
)

/** Estado de la sección "Descubrir" de Inicio. */
data class DiscoverUi(
    val loading: Boolean = false,
    val sections: List<DiscoverSection> = emptyList(),
    val genres: List<String> = Discover.DEFAULT_GENRES,
    val selectedGenre: String? = null,
    val genreItems: List<SearchResult> = emptyList(),
    val genreLoading: Boolean = false,
    val error: String? = null,
    val loadedAt: Long = 0,
)

/** ¿El enlace es una lista o álbum? Esos sí hay que analizarlos antes de abrir la hoja. */
private fun looksLikePlaylist(url: String): Boolean {
    val u = url.lowercase()
    return "/playlist" in u ||
        ("list=" in u && "v=" !in u && "youtu.be/" !in u) ||
        "/sets/" in u ||
        "/album/" in u ||
        "/channel/" in u ||
        "/@" in u
}

private fun platformOf(url: String): String? {
    val u = url.lowercase()
    return when {
        "youtube.com" in u || "youtu.be" in u -> "Youtube"
        "soundcloud.com" in u -> "Soundcloud"
        "vimeo.com" in u -> "Vimeo"
        "dailymotion.com" in u -> "Dailymotion"
        else -> null
    }
}

/** Lo que el usuario eligió en la hoja de formatos. */
data class Choice(
    val video: Boolean,
    val container: String,
    val quality: Int,
    val estimated: Long,
    val entries: List<PlaylistEntry> = emptyList(),
    val createPlaylist: Boolean = false,
)

private val ACTIVE =
    setOf(
        DownloadState.QUEUED,
        DownloadState.ANALYZING,
        DownloadState.DOWNLOADING,
        DownloadState.PROCESSING,
        DownloadState.PAUSED,
    )

fun extractUrls(text: String): List<String> =
    Regex("""https?://[^\s<>"']+""")
        .findAll(text)
        .map { it.value.trimEnd('.', ',', ')', ']', ';', '!', '?') }
        .distinct()
        .toList()

class HomeViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val c = app.container

    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()

    val active: StateFlow<List<DownloadEntity>> =
        c.downloads.all
            .map { list -> list.filter { it.st in ACTIVE } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recent: StateFlow<List<TrackEntity>> =
        c.library.tracks
            .map { it.take(4) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> =
        c.settings.flow
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var lastClip: String? = null

    // ------------------------------------------------------------------ Descubrir

    private val _discover = MutableStateFlow(DiscoverUi())
    val discover: StateFlow<DiscoverUi> = _discover.asStateFlow()
    private var discoverJob: Job? = null
    private var genreJob: Job? = null

    /** Identificadores y títulos de lo que ya tienes, para no recomendártelo otra vez. */
    private var ownedIds: Set<String> = emptySet()
    private var ownedTitles: Set<String> = emptySet()

    private fun isNew(r: SearchResult): Boolean {
        val id = YouTubeSearch.idOf(r.url)
        return (id == null || id !in ownedIds) && Discover.norm(r.title) !in ownedTitles
    }

    /**
     * Arma las recomendaciones:
     * 1. Busca tus artistas más escuchados y, por cada uno, el "Mix" de YouTube de una canción suya.
     * 2. Averigua sus géneros en MusicBrainz y muestra canciones del género principal.
     * Se recalcula como máximo cada 2 horas, salvo que toques "Actualizar".
     */
    fun loadDiscover(force: Boolean = false) {
        val d = _discover.value
        if (!force && (d.loading || System.currentTimeMillis() - d.loadedAt < 2 * 60 * 60 * 1000L)) return
        discoverJob?.cancel()
        discoverJob =
            viewModelScope.launch {
                _discover.update { it.copy(loading = true, error = null, sections = if (force) emptyList() else it.sections) }
                val app = getApplication<Application>()
                val tracks = runCatching { c.library.tracksOnce() }.getOrDefault(emptyList())
                ownedIds = tracks.mapNotNull { YouTubeSearch.idOf(it.sourceUrl) }.toSet()
                ownedTitles = tracks.map { Discover.norm(it.title) }.toSet()

                // Artistas ordenados por cuánto los escuchas y qué tan recientes son.
                val now = System.currentTimeMillis()
                val byArtist =
                    tracks
                        .filter { it.artist.isNotBlank() }
                        .groupBy { Discover.cleanArtist(it.artist) }
                        .filterKeys { it.isNotBlank() }
                val artists =
                    byArtist
                        .mapValues { (_, list) ->
                            list.sumOf { t ->
                                val days = (now - t.addedAt) / 86_400_000.0
                                t.playCount * 2.0 + 1.0 + (if (days < 14) 2.0 else 0.0)
                            }
                        }.entries
                        .sortedByDescending { it.value }
                        .map { it.key }
                        .take(3)

                val sections = mutableListOf<DiscoverSection>()
                var failures = 0
                for (artist in artists.take(2)) {
                    val seedTrack = byArtist[artist].orEmpty().maxByOrNull { it.playCount }
                    val seedId =
                        YouTubeSearch.idOf(seedTrack?.sourceUrl)
                            ?: runCatching {
                                Engine
                                    .search(app, artist, SearchPlatform.YOUTUBE)
                                    .results
                                    .firstOrNull()
                                    ?.let { YouTubeSearch.idOf(it.url) }
                            }.getOrNull()
                    if (seedId == null) {
                        failures++
                        continue
                    }
                    val items =
                        runCatching { Engine.similar(app, seedId, 25) }
                            .onFailure { failures++ }
                            .getOrDefault(emptyList())
                            .filter { isNew(it) }
                            .take(15)
                    if (items.isNotEmpty()) {
                        sections += DiscoverSection("a:$artist", "Porque escuchas $artist", "Canciones parecidas", items)
                        _discover.update { it.copy(sections = sections.toList()) }
                    }
                }

                // Géneros de tus artistas (MusicBrainz pide no más de una consulta por segundo).
                val detected = mutableListOf<String>()
                for ((i, artist) in artists.withIndex()) {
                    if (i > 0) delay(1100)
                    detected += runCatching { Discover.genresOf(artist) }.getOrDefault(emptyList())
                }
                val ranked =
                    detected
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .map { it.key }
                val genres = (ranked + Discover.DEFAULT_GENRES).distinct().take(12)
                val chosen = _discover.value.selectedGenre?.takeIf { it in genres } ?: ranked.firstOrNull() ?: genres.first()

                _discover.update {
                    it.copy(
                        loading = false,
                        sections = sections.toList(),
                        genres = genres,
                        loadedAt = System.currentTimeMillis(),
                        error =
                            if (sections.isEmpty() && artists.isNotEmpty() && failures > 0) {
                                "No se pudieron cargar las recomendaciones. Revisa tu conexión."
                            } else {
                                null
                            },
                    )
                }
                selectGenre(chosen)
            }
    }

    /** Muestra canciones populares del género elegido. */
    fun selectGenre(genre: String) {
        genreJob?.cancel()
        _discover.update { it.copy(selectedGenre = genre, genreLoading = true, genreItems = emptyList()) }
        genreJob =
            viewModelScope.launch {
                val items =
                    runCatching {
                        Engine.search(getApplication<Application>(), "$genre éxitos", SearchPlatform.YOUTUBE).results
                    }.getOrDefault(emptyList())
                        .filter { !it.isLive && isNew(it) }
                        .take(20)
                _discover.update { if (it.selectedGenre == genre) it.copy(genreItems = items, genreLoading = false) else it }
            }
    }

    fun setText(t: String) = _ui.update { it.copy(text = t, error = null) }

    /** Revisa el portapapeles al volver a la app y, si hay un enlace nuevo, lo analiza en silencio. */
    fun onClipboard(text: String?) {
        if (!settings.value.clipboard) return
        val url = extractUrls(text ?: "").firstOrNull() ?: return
        if (url == lastClip) return
        lastClip = url
        _ui.update { it.copy(clip = url, clipInfo = null) }
        viewModelScope.launch {
            runCatching { Engine.analyze(getApplication<Application>(), url) }.onSuccess { info ->
                _ui.update { if (it.clip == url) it.copy(clipInfo = info) else it }
            }
        }
    }

    fun dismissClip() = _ui.update { it.copy(clip = null, clipInfo = null) }

    private var searchJob: Job? = null

    /** Botón de la barra: si hay un enlace lo analiza; si es texto, lo busca. */
    fun submit(raw: String = _ui.value.text) {
        when {
            extractUrls(raw).isNotEmpty() -> analyze(raw)
            raw.isNotBlank() -> search(raw.trim(), _ui.value.platform, _ui.value.kind)
        }
    }

    fun setPlatform(p: SearchPlatform) {
        val q = _ui.value.searchQuery
        // Los álbumes solo se buscan en YouTube.
        val k = if (p == SearchPlatform.SOUNDCLOUD) SearchKind.SONGS else _ui.value.kind
        _ui.update { it.copy(platform = p, kind = k) }
        if (q != null) search(q, p, k)
    }

    fun setKind(k: SearchKind) {
        val q = _ui.value.searchQuery
        _ui.update { it.copy(kind = k) }
        if (q != null) search(q, _ui.value.platform, k)
    }

    fun clearSearch() {
        searchJob?.cancel()
        moreJob?.cancel()
        _ui.update {
            it.copy(
                searchQuery = null,
                results = emptyList(),
                searching = false,
                searchError = null,
                text = "",
                openingUrl = null,
                nextToken = null,
                loadingMore = false,
                selecting = false,
                selected = emptySet(),
            )
        }
    }

    private var moreJob: Job? = null

    /** Trae la siguiente página de resultados. Se llama sola al llegar al final de la lista. */
    fun loadMore() {
        val st = _ui.value
        val token = st.nextToken ?: return
        if (st.loadingMore || st.searching) return
        _ui.update { it.copy(loadingMore = true) }
        moreJob =
            viewModelScope.launch {
                try {
                    val page = Engine.searchMore(token, st.kind)
                    _ui.update {
                        if (it.nextToken != token) return@update it
                        val merged = (it.results + page.results).distinctBy { r -> r.url }
                        // Si la página no trajo nada nuevo, no seguir pidiendo.
                        val next = if (merged.size == it.results.size) null else page.next
                        it.copy(results = merged, nextToken = next, loadingMore = false)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _ui.update { it.copy(loadingMore = false, nextToken = null) }
                }
            }
    }

    // ---------------- selección múltiple

    fun toggleSelecting() = _ui.update { it.copy(selecting = !it.selecting, selected = emptySet()) }

    fun toggleSelected(url: String) = _ui.update { it.copy(selected = if (url in it.selected) it.selected - url else it.selected + url) }

    fun selectAll() =
        _ui.update { st ->
            val all =
                st.results
                    .filter { !it.isPlaylist }
                    .map { it.url }
                    .toSet()
            st.copy(selected = if (st.selected.size == all.size) emptySet() else all)
        }

    fun downloadSelected() {
        val st = _ui.value
        startBatch(st.results.filter { it.url in st.selected })
    }

    /** Abre la hoja de formatos para varias canciones a la vez. */
    fun startBatch(items: List<SearchResult>) {
        val songs = items.filter { !it.isPlaylist }
        if (songs.isEmpty()) return
        _ui.update { it.copy(batchItems = songs, batch = songs.map { r -> r.url }) }
    }

    private fun search(
        query: String,
        platform: SearchPlatform,
        kind: SearchKind,
    ) {
        searchJob?.cancel()
        moreJob?.cancel()
        _ui.update {
            it.copy(
                searchQuery = query,
                platform = platform,
                kind = kind,
                searching = true,
                searchError = null,
                results = emptyList(),
                nextToken = null,
                loadingMore = false,
                selected = emptySet(),
                error = null,
            )
        }
        searchJob =
            viewModelScope.launch {
                try {
                    val page = Engine.search(getApplication<Application>(), query, platform, kind)
                    val results = page.results
                    _ui.update {
                        it.copy(
                            searching = false,
                            results = results,
                            nextToken = page.next,
                            searchError = if (results.isEmpty()) "No encontramos nada con «$query»." else null,
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val type = Engine.classify(e.message)
                    val msg =
                        if (type == ErrorType.NETWORK) {
                            "Sin conexión. Revisa tu internet e intenta de nuevo."
                        } else {
                            "No se pudo buscar en ${platform.label}. Prueba actualizar el motor en Ajustes."
                        }
                    _ui.update { it.copy(searching = false, searchError = msg) }
                }
            }
    }

    /**
     * Abre la hoja de formatos al instante con lo que ya se sabe (título, miniatura, duración)
     * y carga los formatos y tamaños reales por detrás. Se puede descargar sin esperar.
     */
    private fun openFast(placeholder: MediaInfo) {
        val url = placeholder.url
        val cached = Engine.cached(url)
        if (cached != null) {
            _ui.update { it.copy(info = cached, loadingFormats = false, formatsError = null, error = null) }
            return
        }
        _ui.update { it.copy(info = placeholder, loadingFormats = true, formatsError = null, error = null) }
        viewModelScope.launch {
            try {
                val real = Engine.analyze(getApplication<Application>(), url)
                _ui.update { if (it.info?.url == url) it.copy(info = real, loadingFormats = false) else it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val type = Engine.classify(e.message)
                val msg =
                    if (type.retryable) {
                        "No se pudieron cargar los tamaños. Puedes descargar igual."
                    } else {
                        Engine.userMessage(type)
                    }
                _ui.update { if (it.info?.url == url) it.copy(loadingFormats = false, formatsError = msg) else it }
            }
        }
    }

    /** Un álbum o lista: hay que leer sus canciones antes de abrir la hoja. */
    private fun openPlaylist(r: SearchResult) {
        if (_ui.value.openingUrl != null) return
        Engine.cached(r.url)?.let { cached ->
            _ui.update { it.copy(info = cached) }
            return
        }
        _ui.update { it.copy(openingUrl = r.url, error = null) }
        viewModelScope.launch {
            try {
                val info = Engine.analyze(getApplication<Application>(), r.url)
                _ui.update { it.copy(openingUrl = null, info = info) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = Engine.userMessage(Engine.classify(e.message))
                _ui.update { it.copy(openingUrl = null, error = msg) }
            }
        }
    }

    /** Abre la hoja de formatos para un resultado de búsqueda o una recomendación. */
    fun openResult(
        r: SearchResult,
        platform: SearchPlatform = _ui.value.platform,
    ) {
        if (r.isPlaylist) {
            openPlaylist(r)
            return
        }
        val audioOnly = platform == SearchPlatform.SOUNDCLOUD
        openFast(
            MediaInfo(
                url = r.url,
                title = r.title,
                author = r.author,
                platform = if (audioOnly) "Soundcloud" else "Youtube",
                durationSec = r.durationSec,
                thumbnail = r.thumbnail,
                entries = emptyList(),
                videoQualities = if (audioOnly) emptyList() else listOf(1080, 720, 480, 360),
                videoSizes = emptyMap(),
                hasVideo = !audioOnly,
                sourceAudioKbps = 0,
            ),
        )
    }

    fun analyze(raw: String = _ui.value.text) {
        val urls = extractUrls(raw)
        when {
            urls.isEmpty() -> _ui.update { it.copy(error = Engine.userMessage(ErrorType.INVALID_URL)) }
            urls.size > 1 -> _ui.update { it.copy(batch = urls, error = null) }
            else -> {
                val url = urls.first()
                val cached = _ui.value.clipInfo?.takeIf { _ui.value.clip == url } ?: Engine.cached(url)
                if (cached != null) {
                    _ui.update { it.copy(info = cached, error = null) }
                    return
                }
                // Enlace de un solo vídeo o canción: abrir la hoja ya y cargar los detalles por detrás.
                if (!looksLikePlaylist(url)) {
                    val audioOnly = platformOf(url) == "Soundcloud"
                    openFast(
                        MediaInfo(
                            url = url,
                            title = url.substringAfter("://").removePrefix("www.").take(60),
                            author = null,
                            platform = platformOf(url),
                            durationSec = 0,
                            thumbnail = null,
                            entries = emptyList(),
                            videoQualities = if (audioOnly) emptyList() else listOf(1080, 720, 480, 360),
                            videoSizes = emptyMap(),
                            hasVideo = !audioOnly,
                            sourceAudioKbps = 0,
                        ),
                    )
                    return
                }
                _ui.update { it.copy(analyzing = true, error = null) }
                viewModelScope.launch {
                    try {
                        val info = Engine.analyze(getApplication<Application>(), url)
                        _ui.update { it.copy(analyzing = false, info = info) }
                    } catch (e: Exception) {
                        val type = Engine.classify(e.message)
                        _ui.update { it.copy(analyzing = false, error = Engine.userMessage(type)) }
                    }
                }
            }
        }
    }

    fun closeSheet() =
        _ui.update { it.copy(info = null, batch = emptyList(), batchItems = emptyList(), loadingFormats = false, formatsError = null) }

    fun download(choice: Choice) {
        val state = _ui.value
        viewModelScope.launch {
            val kind = if (choice.video) "VIDEO" else "AUDIO"
            val info = state.info
            val items =
                when {
                    info != null && info.isPlaylist -> {
                        val pid = if (choice.createPlaylist) c.library.createPlaylist(info.title) else null
                        choice.entries.map { e ->
                            DownloadEntity(
                                url = e.url,
                                title = e.title,
                                author = e.author ?: info.author,
                                thumbnail = e.thumbnail,
                                platform = info.platform,
                                kind = kind,
                                container = choice.container,
                                quality = choice.quality,
                                targetPlaylistId = pid,
                            )
                        }
                    }
                    info != null ->
                        listOf(
                            DownloadEntity(
                                url = info.url,
                                title = info.title,
                                author = info.author,
                                thumbnail = info.thumbnail,
                                platform = info.platform,
                                kind = kind,
                                container = choice.container,
                                quality = choice.quality,
                                estimatedBytes = choice.estimated,
                            ),
                        )
                    state.batchItems.isNotEmpty() ->
                        state.batchItems.map { r ->
                            DownloadEntity(
                                url = r.url,
                                title = r.title,
                                author = r.author,
                                thumbnail = r.thumbnail,
                                platform = platformOf(r.url),
                                kind = kind,
                                container = choice.container,
                                quality = choice.quality,
                            )
                        }
                    else ->
                        state.batch.map { u ->
                            DownloadEntity(url = u, title = u, kind = kind, container = choice.container, quality = choice.quality)
                        }
                }
            c.downloads.enqueue(items)
            _ui.update {
                it.copy(
                    info = null,
                    batch = emptyList(),
                    batchItems = emptyList(),
                    selecting = false,
                    selected = emptySet(),
                    text = "",
                    clip = null,
                    clipInfo = null,
                    loadingFormats = false,
                    formatsError = null,
                )
            }
        }
    }
}
