package com.bruma.app.ui.player

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.bruma.app.container
import com.bruma.app.ui.common.BI
import com.bruma.app.ui.theme.T

/** Reproductor de vídeo a pantalla completa. Admite imagen en imagen (PiP) al salir de la app. */
@Composable
fun VideoScreen(
    onBack: () -> Unit,
    onVideoActive: (Boolean) -> Unit,
    pip: Boolean = false,
) {
    val connection = LocalContext.current.container.player
    val state by connection.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    LaunchedEffect(Unit) { connection.connect() }

    DisposableEffect(Unit) {
        onVideoActive(true)
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            onVideoActive(false)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val mc = connection.controller
        if (mc == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = mc
                        keepScreenOn = true
                        useController = !pip
                    }
                },
                update = { v ->
                    if (v.player !== mc) v.player = mc
                    v.useController = !pip
                },
                onRelease = { v -> v.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!pip) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    BI.Back,
                    "Volver",
                    tint = Color.White,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x55000000))
                            .clickable { onBack() }
                            .padding(9.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.title, style = T.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.artist.isNotBlank()) Text(state.artist, style = T.tiny, color = Color(0xCCFFFFFF), maxLines = 1)
                }
            }
        }
    }
}
