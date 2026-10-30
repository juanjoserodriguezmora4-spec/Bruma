package com.bruma.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.bruma.app.container
import com.bruma.app.ui.common.BI
import com.bruma.app.ui.common.Badge
import com.bruma.app.ui.common.GlassBackground
import com.bruma.app.ui.common.IconChip
import com.bruma.app.ui.common.ListRow
import com.bruma.app.ui.common.PrimaryButton
import com.bruma.app.ui.common.Thumb
import com.bruma.app.ui.common.fmtDuration
import com.bruma.app.ui.common.glass
import com.bruma.app.ui.theme.LocalBruma
import com.bruma.app.ui.theme.T
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenVideo: () -> Unit,
) {
    val c = LocalBruma.current
    val player = LocalContext.current.container.player
    val s by player.state.collectAsStateWithLifecycle()

    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var sleepMenu by remember { mutableStateOf(false) }

    LaunchedEffect(s.currentId, s.isPlaying) {
        while (true) {
            if (!dragging) position = player.position()
            delay(500)
        }
    }

    GlassBackground(seed = 1) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(BI.Down, onBack, description = "Cerrar reproductor")
                    Text(
                        if (s.queue.size > 1) "Reproduciendo · ${s.index + 1} de ${s.queue.size}" else "Reproduciendo",
                        style = T.small,
                        color = c.muted,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                    )
                    Box {
                        IconChip(
                            BI.Timer,
                            { sleepMenu = true },
                            tint = if (s.sleepAt != null || s.sleepEndOfTrack) c.accent else null,
                            description = "Temporizador",
                        )
                        DropdownMenu(sleepMenu, { sleepMenu = false }) {
                            listOf(15, 30, 60).forEach { m ->
                                DropdownMenuItem(text = { Text("En $m minutos") }, onClick = {
                                    player.sleep(m)
                                    sleepMenu = false
                                })
                            }
                            DropdownMenuItem(text = { Text("Al terminar la canción") }, onClick = {
                                player.sleep(-1)
                                sleepMenu = false
                            })
                            DropdownMenuItem(text = { Text("Desactivar") }, onClick = {
                                player.sleep(null)
                                sleepMenu = false
                            })
                        }
                    }
                }
            }

            if (s.currentId == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No hay nada sonando", style = T.h2, color = c.text)
                        Spacer(Modifier.height(6.dp))
                        Text("Elige una canción en tu biblioteca.", style = T.small, color = c.muted)
                    }
                }
                return@LazyColumn
            }

            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Thumb(
                        s.artwork,
                        s.title,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        corner = 28.dp,
                        icon = if (s.artwork == null) BI.Wave else null,
                    )
                }
            }

            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = T.big, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(s.artist.ifBlank { " " }, style = T.body, color = c.muted, maxLines = 1)
                        }
                        Badge("Sin conexión")
                    }
                    if (s.sleepAt != null || s.sleepEndOfTrack) {
                        val left = s.sleepAt?.let { ((it - System.currentTimeMillis()) / 60000).coerceAtLeast(0) }
                        Text(
                            if (s.sleepEndOfTrack) "Se detiene al terminar esta canción" else "Se detiene en $left min",
                            style = T.tiny,
                            color = c.accent,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            item {
                Column {
                    val dur = s.durationMs.coerceAtLeast(1L)
                    Slider(
                        value = if (dragging) dragValue else (position.toFloat() / dur).coerceIn(0f, 1f),
                        onValueChange = {
                            dragging = true
                            dragValue = it
                        },
                        onValueChangeFinished = {
                            player.seek((dragValue * dur).toLong())
                            position = (dragValue * dur).toLong()
                            dragging = false
                        },
                        colors =
                            SliderDefaults.colors(
                                thumbColor = c.accent,
                                activeTrackColor = c.accent,
                                inactiveTrackColor = c.track,
                            ),
                    )
                    Row {
                        Text(fmtDuration(position / 1000), style = T.tiny, color = c.muted, modifier = Modifier.weight(1f))
                        Text(fmtDuration(s.durationMs / 1000), style = T.tiny, color = c.muted)
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlIcon(BI.Shuffle, if (s.shuffle) c.accent else c.muted, "Aleatorio") { player.setShuffle(!s.shuffle) }
                    ControlIcon(BI.Prev, c.text, "Anterior", size = 30) { player.previous() }
                    Box(
                        Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(if (c.dark) Color.White else c.accent)
                            .clickable { player.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (s.isPlaying) BI.Pause else BI.Play,
                            if (s.isPlaying) "Pausar" else "Reproducir",
                            tint = if (c.dark) Color(0xFF0B0F1A) else c.onAccent,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    ControlIcon(BI.Next, c.text, "Siguiente", size = 30) { player.next() }
                    ControlIcon(
                        if (s.repeat == Player.REPEAT_MODE_ONE) BI.RepeatOne else BI.Repeat,
                        if (s.repeat == Player.REPEAT_MODE_OFF) c.muted else c.accent,
                        "Repetición",
                    ) { player.cycleRepeat() }
                }
            }

            if (s.isVideo) {
                item { PrimaryButton("Ver el vídeo", onOpenVideo, Modifier.fillMaxWidth(), icon = BI.Fullscreen) }
            }

            val upcoming =
                s.queue
                    .withIndex()
                    .drop(s.index + 1)
                    .take(30)
            if (upcoming.isNotEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .glass()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text("A continuación", style = T.small, color = c.muted, modifier = Modifier.padding(4.dp))
                        upcoming.forEach { (i, q) ->
                            ListRow(
                                title = q.title,
                                subtitle = q.artist.ifBlank { " " },
                                leading = { Thumb(null, q.title, Modifier.size(34.dp), corner = 8.dp) },
                                onClick = { player.jumpTo(i) },
                                trailing = {
                                    Icon(
                                        BI.Close,
                                        "Quitar de la cola",
                                        tint = c.muted,
                                        modifier =
                                            Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { player.removeAt(i) }
                                                .padding(7.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    description: String,
    size: Int = 24,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size.dp))
    }
}
