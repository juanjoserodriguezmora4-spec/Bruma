package com.bruma.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bruma.app.container
import com.bruma.app.data.db.PlaylistSummary
import com.bruma.app.data.db.TrackEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Listas automáticas que no se guardan en la base de datos. */
const val AUTO_RECENT = -1L
const val AUTO_TOP = -2L

class LibraryViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val c = app.container
    private val hidden = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingDeletes = mutableMapOf<Long, Job>()

    val tracks: StateFlow<List<TrackEntity>> =
        combine(c.library.tracks, hidden) { list, h -> list.filter { it.id !in h } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistSummary>> =
        c.library.playlists
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(
        name: String,
        thenAdd: Long? = null,
    ) = viewModelScope.launch {
        val id = c.library.createPlaylist(name)
        if (thenAdd != null) c.library.addToPlaylist(id, thenAdd)
    }

    fun addToPlaylist(
        playlistId: Long,
        trackId: Long,
    ) = viewModelScope.launch { c.library.addToPlaylist(playlistId, trackId) }

    fun updateTags(
        id: Long,
        title: String,
        artist: String,
    ) = viewModelScope.launch { c.library.updateTags(id, title, artist) }

    /** Oculta la pista y la borra de verdad a los 5 segundos, salvo que el usuario toque «Deshacer». */
    fun scheduleDelete(track: TrackEntity) {
        hidden.value = hidden.value + track.id
        pendingDeletes[track.id] =
            c.appScope.launch {
                delay(5000)
                c.library.deleteTrack(track)
                pendingDeletes.remove(track.id)
                hidden.value = hidden.value - track.id
            }
    }

    fun undoDelete(id: Long) {
        pendingDeletes.remove(id)?.cancel()
        hidden.value = hidden.value - id
    }
}
