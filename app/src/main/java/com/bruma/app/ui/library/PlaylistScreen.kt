package com.bruma.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bruma.app.container
import com.bruma.app.data.db.TrackEntity
import com.bruma.app.ui.common.BI
import com.bruma.app.ui.common.EmptyState
import com.bruma.app.ui.common.GlassBackground
import com.bruma.app.ui.common.IconChip
import com.bruma.app.ui.common.PrimaryButton
import com.bruma.app.ui.common.TextInputDialog
import com.bruma.app.ui.common.Thumb
import com.bruma.app.ui.common.fmtDuration
import com.bruma.app.ui.common.glass
import com.bruma.app.ui.theme.LocalBruma
import com.bruma.app.ui.theme.T
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PlaylistScreen(
    id: Long,
    onBack: () -> Unit,
    onOpenTrack: (List<TrackEntity>, Int, Boolean) -> Unit,
) {
    val c = LocalBruma.current
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val auto = id < 0

    val tracksFlow =
        remember(id) {
            when (id) {
                AUTO_RECENT -> app.library.tracks.map { it.take(50) }
                AUTO_TOP -> app.library.tracks.map { l -> l.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(50) }
                else -> app.library.playlistTracks(id)
            }
        }
    val nameFlow =
        remember(id) {
            when (id) {
                AUTO_RECENT -> flowOf("Añadido recientemente")
                AUTO_TOP -> flowOf("Más escuchadas")
                else -> app.library.playlist(id).map { it?.name ?: "" }
            }
        }
    val tracks by tracksFlow.collectAsState(initial = emptyList())
    val name by nameFlow.collectAsState(initial = "")
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val total = tracks.sumOf { it.durationSec }.toLong()

    GlassBackground(seed = 3) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(BI.Back, onBack, description = "Volver")
                    Spacer(Modifier.weight(1f))
                    if (!auto) {
                        Box {
                            IconChip(BI.More, { menu = true }, description = "Opciones de la lista")
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Renombrar") }, onClick = {
                                    menu = false
                                    renaming = true
                                })
                                DropdownMenuItem(text = { Text("Borrar lista") }, onClick = {
                                    menu = false
                                    confirmDelete = true
                                })
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Thumb(
                        tracks.firstOrNull()?.coverPath?.let { File(it) },
                        name,
                        Modifier.size(92.dp),
                        corner = 20.dp,
                        icon = BI.Music,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = T.big, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${tracks.size} elementos · ${fmtDuration(total)}", style = T.small, color = c.muted)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        "Reproducir",
                        { onOpenTrack(tracks, 0, false) },
                        Modifier.weight(1f),
                        icon = BI.Play,
                        enabled = tracks.isNotEmpty(),
                    )
                    PrimaryButton(
                        "Aleatorio",
                        { onOpenTrack(tracks, 0, true) },
                        Modifier.weight(1f),
                        icon = BI.Shuffle,
                        enabled = tracks.isNotEmpty(),
                    )
                }
            }
            if (tracks.isEmpty()) {
                item {
                    EmptyState(
                        BI.Music,
                        "Lista vacía",
                        "En la Biblioteca, toca los tres puntos de una canción y elige «Añadir a una lista».",
                    )
                }
            } else {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .glass()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        tracks.forEachIndexed { i, t ->
                            TrackRow(
                                t = t,
                                onClick = { onOpenTrack(tracks, i, false) },
                                extra =
                                    if (auto) {
                                        null
                                    } else {
                                        {
                                            IconChip(
                                                BI.Up,
                                                { scope.launch { app.library.move(id, t.id, -1) } },
                                                size = 28.dp,
                                                description = "Subir",
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            IconChip(
                                                BI.Down,
                                                { scope.launch { app.library.move(id, t.id, 1) } },
                                                size = 28.dp,
                                                description = "Bajar",
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            IconChip(BI.Close, {
                                                scope.launch { app.library.removeFromPlaylist(id, t.id) }
                                            }, tint = c.muted, size = 28.dp, description = "Quitar de la lista")
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Renombrar lista",
            initial = name,
            confirm = "Guardar",
            label = "Nombre",
            onDismiss = { renaming = false },
            onConfirm = { n ->
                scope.launch { app.library.renamePlaylist(id, n) }
                renaming = false
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar la lista?", style = T.h2) },
            text = { Text("Las canciones siguen en tu biblioteca; solo se borra la lista.", style = T.body) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { app.library.deletePlaylist(id) }
                    onBack()
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
