package com.bruma.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bruma.app.player.PlayerConnection
import com.bruma.app.player.PlayerUiState
import com.bruma.app.ui.theme.LocalBruma
import com.bruma.app.ui.theme.T
import kotlinx.coroutines.delay

data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val TABS =
    listOf(
        Tab("home", "Inicio", BI.Home),
        Tab("downloads", "Descargas", BI.Download),
        Tab("library", "Biblioteca", BI.Music),
        Tab("settings", "Ajustes", BI.Gear),
    )

@Composable
fun BottomNav(
    current: String?,
    activeDownloads: Int,
    onSelect: (String) -> Unit,
) {
    val c = LocalBruma.current
    Row(
        Modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp), strong = true)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        TABS.forEach { t ->
            val on = current == t.route || (t.route == "library" && current?.startsWith("playlist") == true)
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(t.route) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, t.label, tint = if (on) c.accent else c.muted, modifier = Modifier.size(22.dp))
                val label = if (t.route == "downloads" && activeDownloads > 0) "${t.label} · $activeDownloads" else t.label
                Text(label, style = T.tiny, color = if (on) c.accent else c.muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    state: PlayerUiState,
    connection: PlayerConnection,
    onOpen: () -> Unit,
) {
    val c = LocalBruma.current
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.currentId, state.isPlaying) {
        while (true) {
            position = connection.position()
            delay(1000)
        }
    }
    Column(
        Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), strong = true)
            .clickable { onOpen() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(state.artwork, state.title, Modifier.size(36.dp), corner = 9.dp, icon = if (state.isVideo) BI.Play else null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.title, style = T.small, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.artist.isNotBlank()) Text(state.artist, style = T.tiny, color = c.muted, maxLines = 1)
            }
            Icon(
                if (state.isPlaying) BI.Pause else BI.Play,
                if (state.isPlaying) "Pausar" else "Reproducir",
                tint = c.text,
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { connection.toggle() }
                        .padding(8.dp),
            )
            Icon(
                BI.Next,
                "Siguiente",
                tint = c.text,
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { connection.next() }
                        .padding(9.dp),
            )
        }
        val fraction = if (state.durationMs > 0) position.toFloat() / state.durationMs else 0f
        ProgressLine(fraction, c.accent, Modifier.padding(top = 6.dp), height = 3.dp)
    }
}
