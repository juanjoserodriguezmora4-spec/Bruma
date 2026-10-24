package com.bruma.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadState { QUEUED, ANALYZING, DOWNLOADING, PROCESSING, PAUSED, COMPLETED, FAILED, CANCELED }

/** Un trabajo de descarga en la cola. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val author: String? = null,
    val thumbnail: String? = null,
    val platform: String? = null,
    /** VIDEO o AUDIO */
    val kind: String,
    /** MP4, WEBM, M4V, MP3, M4A o FLAC */
    val container: String,
    /** Altura en píxeles (vídeo) o kbps (audio). 0 = calidad original. */
    val quality: Int,
    val state: String = DownloadState.QUEUED.name,
    val progress: Float = 0f,
    val totalBytes: Long = 0,
    val speed: Long = 0,
    val eta: Long = -1,
    val message: String? = null,
    /** USER si la pausó el usuario; si no, el tipo de error. */
    val errorType: String? = null,
    val attempts: Int = 0,
    val estimatedBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val trackId: Long? = null,
    val targetPlaylistId: Long? = null,
)

val DownloadEntity.st: DownloadState
    get() = runCatching { DownloadState.valueOf(state) }.getOrDefault(DownloadState.FAILED)

val DownloadEntity.isVideo: Boolean get() = kind == "VIDEO"

/** Un archivo descargado que forma parte de la biblioteca. */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val uri: String,
    val mime: String,
    val isVideo: Boolean,
    val coverPath: String? = null,
    val source: String? = null,
    val qualityLabel: String = "",
    val sizeBytes: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPositionMs: Long = 0,
    val playCount: Int = 0,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackRef(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

/** Resultado de consulta: lista con su número de elementos y la carátula del primero. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
    val cover: String?,
)
