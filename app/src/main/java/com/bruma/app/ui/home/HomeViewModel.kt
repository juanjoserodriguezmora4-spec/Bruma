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
import com.bruma.app.engine.Engine
import com.bruma.app.engine.ErrorType
import com.bruma.app.engine.MediaInfo
import com.bruma.app.engine.PlaylistEntry
import com.bruma.app.engine.SearchPlatform
import com.bruma.app.engine.SearchResult
import kotlinx.coroutines.Job
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
)

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
            raw.isNotBlank() -> search(raw.trim(), _ui.value.platform)
        }
    }

    fun setPlatform(p: SearchPlatform) {
        val q = _ui.value.searchQuery
        _ui.update { it.copy(platform = p) }
        if (q != null) search(q, p)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _ui.update {
            it.copy(searchQuery = null, results = emptyList(), searching = false, searchError = null, text = "", openingUrl = null)
        }
    }

    private fun search(
        query: String,
        platform: SearchPlatform,
    ) {
        searchJob?.cancel()
        _ui.update {
            it.copy(searchQuery = query, platform = platform, searching = true, searchError = null, results = emptyList(), error = null)
        }
        searchJob =
            viewModelScope.launch {
                try {
                    val results = Engine.search(getApplication<Application>(), query, platform)
                    _ui.update {
                        it.copy(
                            searching = false,
                            results = results,
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

    /** Abre la hoja de formatos para un resultado de búsqueda. */
    fun openResult(r: SearchResult) {
        if (_ui.value.openingUrl != null) return
        _ui.update { it.copy(openingUrl = r.url, error = null) }
        viewModelScope.launch {
            try {
                val info = Engine.analyze(getApplication<Application>(), r.url)
                _ui.update { it.copy(openingUrl = null, info = info) }
            } catch (e: Exception) {
                val type = Engine.classify(e.message)
                _ui.update { it.copy(openingUrl = null, searchError = Engine.userMessage(type)) }
            }
        }
    }

    fun analyze(raw: String = _ui.value.text) {
        val urls = extractUrls(raw)
        when {
            urls.isEmpty() -> _ui.update { it.copy(error = Engine.userMessage(ErrorType.INVALID_URL)) }
            urls.size > 1 -> _ui.update { it.copy(batch = urls, error = null) }
            else -> {
                val url = urls.first()
                val cached = _ui.value.clipInfo?.takeIf { _ui.value.clip == url }
                if (cached != null) {
                    _ui.update { it.copy(info = cached, error = null) }
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

    fun closeSheet() = _ui.update { it.copy(info = null, batch = emptyList()) }

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
                                author = info.author,
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
                    else ->
                        state.batch.map { u ->
                            DownloadEntity(url = u, title = u, kind = kind, container = choice.container, quality = choice.quality)
                        }
                }
            c.downloads.enqueue(items)
            _ui.update { it.copy(info = null, batch = emptyList(), text = "", clip = null, clipInfo = null) }
        }
    }
}
