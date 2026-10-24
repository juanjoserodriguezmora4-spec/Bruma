package com.bruma.app.library

import android.app.Application
import com.bruma.app.data.db.BrumaDatabase
import com.bruma.app.data.db.PlaylistEntity
import com.bruma.app.data.db.PlaylistSummary
import com.bruma.app.data.db.PlaylistTrackRef
import com.bruma.app.data.db.TrackEntity
import com.bruma.app.download.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

class LibraryRepository(
    private val app: Application,
    db: BrumaDatabase,
) {
    private val tracksDao = db.tracks()
    private val playlistDao = db.playlists()

    val tracks: Flow<List<TrackEntity>> = tracksDao.all()
    val playlists: Flow<List<PlaylistSummary>> = playlistDao.summaries()

    fun playlist(id: Long): Flow<PlaylistEntity?> = playlistDao.observe(id)

    fun playlistTracks(id: Long): Flow<List<TrackEntity>> = playlistDao.tracksOf(id)

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name.trim().ifBlank { "Nueva lista" }))

    suspend fun renamePlaylist(
        id: Long,
        name: String,
    ) = playlistDao.rename(id, name.trim())

    suspend fun deletePlaylist(id: Long) {
        playlistDao.clearPlaylist(id)
        playlistDao.deletePlaylist(id)
    }

    suspend fun addToPlaylist(
        playlistId: Long,
        trackId: Long,
    ) {
        val pos = playlistDao.maxPos(playlistId) + 1
        playlistDao.addRef(PlaylistTrackRef(playlistId, trackId, pos))
    }

    suspend fun removeFromPlaylist(
        playlistId: Long,
        trackId: Long,
    ) = playlistDao.removeRef(playlistId, trackId)

    /** Sube (-1) o baja (+1) una pista dentro de la lista. */
    suspend fun move(
        playlistId: Long,
        trackId: Long,
        delta: Int,
    ) {
        val refs = playlistDao.refs(playlistId).toMutableList()
        val i = refs.indexOfFirst { it.trackId == trackId }
        val j = i + delta
        if (i < 0 || j !in refs.indices) return
        Collections.swap(refs, i, j)
        playlistDao.updateRefs(refs.mapIndexed { k, r -> r.copy(position = k) })
    }

    suspend fun updateTags(
        id: Long,
        title: String,
        artist: String,
    ) = tracksDao.updateTags(id, title.trim(), artist.trim())

    /** Borra el archivo del teléfono, su carátula y la entrada de la biblioteca. */
    suspend fun deleteTrack(track: TrackEntity) {
        withContext(Dispatchers.IO) {
            MediaSaver.delete(app, track.uri)
            track.coverPath?.let { File(it).delete() }
        }
        playlistDao.removeTrackEverywhere(track.id)
        tracksDao.delete(track.id)
    }
}
