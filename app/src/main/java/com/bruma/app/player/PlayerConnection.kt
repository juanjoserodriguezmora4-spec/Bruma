package com.bruma.app.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bruma.app.data.db.BrumaDatabase
import com.bruma.app.data.db.TrackEntity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class QueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
)

data class PlayerUiState(
    val currentId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val artwork: String? = null,
    val isVideo: Boolean = false,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueItem> = emptyList(),
    val index: Int = 0,
    val sleepAt: Long? = null,
    val sleepEndOfTrack: Boolean = false,
)

fun TrackEntity.toMediaItem(): MediaItem {
    val mediaUri = Uri.parse(this.uri)
    val metadata =
        MediaMetadata
            .Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(coverPath?.let { Uri.fromFile(File(it)) })
            .setExtras(Bundle().apply { putBoolean("video", isVideo) })
            .build()
    return MediaItem
        .Builder()
        .setMediaId(id.toString())
        .setUri(mediaUri)
        .setMediaMetadata(metadata)
        .setRequestMetadata(
            MediaItem.RequestMetadata
                .Builder()
                .setMediaUri(mediaUri)
                .build(),
        ).build()
}

/** Conecta la interfaz con el servicio de reproducción. Todas las llamadas van en el hilo principal. */
class PlayerConnection(
    private val app: Application,
    private val db: BrumaDatabase,
    private val scope: CoroutineScope,
) {
    private var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private var sleepJob: Job? = null

    private val listener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                refresh()
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                if (_state.value.sleepEndOfTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    controller?.pause()
                    _state.update { it.copy(sleepEndOfTrack = false) }
                }
            }
        }

    fun connect() {
        if (future != null) return
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) {
                future = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            refresh()
            val actions = pending.toList()
            pending.clear()
            actions.forEach { it(c) }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            block(c)
        } else {
            pending += block
            connect()
        }
    }

    fun refresh() {
        val c = controller ?: return
        val items =
            (0 until c.mediaItemCount).map { i ->
                val m = c.getMediaItemAt(i)
                QueueItem(m.mediaId, m.mediaMetadata.title?.toString() ?: "", m.mediaMetadata.artist?.toString() ?: "")
            }
        val cur = c.currentMediaItem
        _state.update {
            it.copy(
                currentId = cur?.mediaId?.toLongOrNull(),
                title = cur?.mediaMetadata?.title?.toString() ?: "",
                artist = cur?.mediaMetadata?.artist?.toString() ?: "",
                artwork = cur?.mediaMetadata?.artworkUri?.toString(),
                isVideo = cur?.mediaMetadata?.extras?.getBoolean("video") == true,
                isPlaying = c.isPlaying,
                durationMs = c.duration.coerceAtLeast(0),
                shuffle = c.shuffleModeEnabled,
                repeat = c.repeatMode,
                queue = items,
                index = c.currentMediaItemIndex,
            )
        }
    }

    fun position(): Long = controller?.currentPosition ?: 0L

    /** Reproduce una lista de pistas empezando en [start]. Retoma la posición en audios y vídeos largos. */
    fun play(
        tracks: List<TrackEntity>,
        start: Int,
        shuffle: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        val index = start.coerceIn(0, tracks.size - 1)
        val first = tracks[index]
        val resume = if (first.durationSec > 600 && first.lastPositionMs < first.durationSec * 1000L - 15_000) first.lastPositionMs else 0L
        withController { c ->
            c.setMediaItems(tracks.map { it.toMediaItem() }, index, resume)
            c.shuffleModeEnabled = shuffle
            c.prepare()
            c.play()
        }
        scope.launch(Dispatchers.IO) { runCatching { db.tracks().incPlay(first.id) } }
    }

    fun toggle() = withController { if (it.isPlaying) it.pause() else it.play() }

    fun next() = withController { it.seekToNext() }

    fun previous() = withController { it.seekToPrevious() }

    fun seek(ms: Long) = withController { it.seekTo(ms) }

    fun jumpTo(index: Int) =
        withController {
            it.seekTo(index, 0)
            it.play()
        }

    fun removeAt(index: Int) = withController { it.removeMediaItem(index) }

    fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }

    fun stop() =
        withController {
            it.stop()
            it.clearMediaItems()
        }

    fun cycleRepeat() =
        withController {
            it.repeatMode =
                when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
        }

    /** null = desactivar, -1 = al terminar la canción, otro valor = minutos. */
    fun sleep(minutes: Int?) {
        sleepJob?.cancel()
        when {
            minutes == null -> _state.update { it.copy(sleepAt = null, sleepEndOfTrack = false) }
            minutes < 0 -> _state.update { it.copy(sleepAt = null, sleepEndOfTrack = true) }
            else -> {
                val at = System.currentTimeMillis() + minutes * 60_000L
                _state.update { it.copy(sleepAt = at, sleepEndOfTrack = false) }
                sleepJob =
                    scope.launch(Dispatchers.Main) {
                        delay(minutes * 60_000L)
                        controller?.pause()
                        _state.update { it.copy(sleepAt = null) }
                    }
            }
        }
    }
}
