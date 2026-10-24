package com.bruma.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.bruma.app.container
import com.bruma.app.download.Notifications
import com.bruma.app.ui.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Servicio que mantiene la música sonando con la pantalla apagada o la app cerrada. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()

        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) savePosition(player)
                }
            },
        )

        val intent =
            Intent(this, MainActivity::class.java)
                .putExtra(Notifications.EXTRA_ROUTE, "player")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending =
            PendingIntent.getActivity(
                this,
                10,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        session =
            MediaSession
                .Builder(this, player)
                .setSessionActivity(pending)
                .setCallback(
                    object : MediaSession.Callback {
                        // Los elementos que llegan desde la interfaz traen la ruta del archivo en requestMetadata.
                        override fun onAddMediaItems(
                            mediaSession: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            mediaItems: MutableList<MediaItem>,
                        ): ListenableFuture<MutableList<MediaItem>> {
                            val resolved =
                                mediaItems
                                    .map { item ->
                                        val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                                        if (uri != null) item.buildUpon().setUri(uri).build() else item
                                    }.toMutableList()
                            return Futures.immediateFuture(resolved)
                        }
                    },
                ).build()
    }

    private fun savePosition(player: Player) {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val pos = player.currentPosition
        scope.launch(Dispatchers.IO) {
            runCatching {
                applicationContext.container.db
                    .tracks()
                    .savePosition(id, pos)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }
}
