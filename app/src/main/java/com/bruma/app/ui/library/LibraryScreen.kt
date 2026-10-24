package com.bruma.app.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bruma.app.data.db.PlaylistSummary
import com.bruma.app.data.db.TrackEntity
import com.bruma.app.ui.common.BI
import com.bruma.app.ui.common.EmptyState
import com.bruma.app.ui.common.GlassBackground
import com.bruma.app.ui.common.IconChip
import com.bruma.app.ui.common.ListRow
import com.bruma.app.ui.common.LocalSnackbar
import com.bruma.app.ui.common.Pill
import com.bruma.app.ui.common.SectionHeader
import com.bruma.app.ui.common.TextInputDialog
import com.bruma.app.ui.common.Thumb
import com.bruma.app.ui.common.fmtDuration
import com.bruma.app.ui.common.glass
import com.bruma.app.ui.theme.LocalBruma
import com.bruma.app.ui.theme.T
import kotlinx.coroutines.launch
import java.io.File

private val SORTS = listOf("Fecha", "Nombre", "Artista", "Duración", "Más escuchadas")

fun sortTracks(
    list: List<TrackEntity>,
    sort: Int,
): List<TrackEntity> =
    when (sort) {
        1 -> list.sortedBy { it.title.lowercase() }
        2 -> list.sortedBy { it.artist.lowercase() }
        3 -> list.sortedByDescending { it.durationSec }
        4 -> list.sortedByDescending { it.playCount }
        else -> list
    }

fun shareTrack(
    context: Context,
    t: TrackEntity,
) {
    val uri = Uri.parse(t.uri)
    val shareUri =
        if (uri.scheme == "file") {
            FileProvider.getUriForFile(context, context.packageName + ".files", File(uri.path ?: return))
        } else {
            uri
        }
    val intent =
        Intent(Intent.ACTION_SEND)
            .setType(t.mime)
            .putExtra(Intent.EXTRA_STREAM, shareUri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Compartir «${t.title}»").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun LibraryScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenTrack: (List<TrackEntity>, Int) -> Unit,
    vm: LibraryViewModel = viewModel(),
) {
    val c = LocalBruma.current
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var newPlaylistFor by remember { mutableStateOf<Long?>(null) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<TrackEntity?>(null) }
    var editing by remember { mutableStateOf<TrackEntity?>(null) }
    var sortMenu by remember { mutableStateOf(false) }

    val visible =
        sortTracks(
            tracks.filter {
                (filter == 0 || (filter == 1 && !it.isVideo) || (filter == 2 && it.isVideo)) &&
                    (query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true))
            },
            sort,
        )

    fun delete(t: TrackEntity) {
        vm.scheduleDelete(t)
        scope.launch {
            val r = snackbar.showSnackbar("Se borró «${t.title}»", "Deshacer", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undoDelete(t.id)
        }
    }

    GlassBackground(seed = 2) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Biblioteca", style = T.h1, color = c.text, modifier = Modifier.weight(1f))
                    IconChip(if (searching) BI.Close else BI.Search, {
                        searching = !searching
                        if (!searching) query = ""
                    }, description = "Buscar")
                }
            }
            if (searching) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(BI.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = T.body.copy(color = c.text),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("Título o artista", style = T.body, color = c.muted)
                                inner()
                            },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Todo", "Música", "Vídeos", "Listas").forEachIndexed { i, name ->
                        Pill(name, filter == i, { filter = i })
                    }
                }
            }

            if (filter == 0 || filter == 3) {
                item { SectionHeader("Tus listas", action = "+ Nueva", onAction = { showNewPlaylist = true }) }
                item {
                    val autos =
                        listOf(
                            PlaylistSummary(
                                AUTO_RECENT,
                                "Añadido recientemente",
                                0,
                                tracks.size.coerceAtMost(50),
                                tracks.firstOrNull()?.coverPath,
                            ),
                            PlaylistSummary(
                                AUTO_TOP,
                                "Más escuchadas",
                                0,
                                tracks.count { it.playCount > 0 },
                                tracks.maxByOrNull { it.playCount }?.coverPath,
                            ),
                        )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(playlists + autos, key = { it.id }) { p ->
                            PlaylistCard(p) { onOpenPlaylist(p.id) }
                        }
                    }
                }
            }

            if (filter != 3) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (filter == 2) {
                                "Vídeos"
                            } else if (filter == 1) {
                                "Música"
                            } else {
                                "Añadido hace poco"
                            },
                            style = T.h2,
                            color = c.text,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { sortMenu = true }
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(BI.Sort, null, tint = c.muted, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ordenar: ${SORTS[sort]}", style = T.tiny, color = c.muted)
                            }
                            DropdownMenu(sortMenu, { sortMenu = false }) {
                                SORTS.forEachIndexed { i, s ->
                                    DropdownMenuItem(text = { Text(s) }, onClick = {
                                        sort = i
                                        sortMenu = false
                                    })
                                }
                            }
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            BI.Music,
                            if (query.isNotBlank()) "Sin resultados" else "Aún no hay nada aquí",
                            if (query.isNotBlank()) "Prueba con otra palabra." else "Lo que descargues aparecerá aquí, listo para escuchar sin conexión.",
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
                            visible.forEachIndexed { i, t ->
                                TrackRow(
                                    t = t,
                                    onClick = {
                                        if (t.isVideo) {
                                            onOpenTrack(listOf(t), 0)
                                        } else {
                                            val audio = visible.filter { !it.isVideo }
                                            onOpenTrack(audio, audio.indexOf(t).coerceAtLeast(0))
                                        }
                                    },
                                    onAddToPlaylist = { addTo = t },
                                    onEdit = { editing = t },
                                    onShare = { runCatching { shareTrack(context, t) } },
                                    onDelete = { delete(t) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewPlaylist || newPlaylistFor != null) {
        TextInputDialog(
            title = "Nueva lista",
            initial = "",
            confirm = "Crear",
            label = "Nombre",
            onDismiss = {
                showNewPlaylist = false
                newPlaylistFor = null
            },
            onConfirm = { name ->
                vm.createPlaylist(name, newPlaylistFor)
                showNewPlaylist = false
                newPlaylistFor = null
            },
        )
    }

    addTo?.let { t ->
        AlertDialog(
            onDismissRequest = { addTo = null },
            title = { Text("Añadir a una lista", style = T.h2) },
            text = {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                newPlaylistFor = t.id
                                addTo = null
                            }.padding(vertical = 12.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(BI.Plus, null, tint = c.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Nueva lista", style = T.title, color = c.accent)
                    }
                    playlists.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    vm.addToPlaylist(p.id, t.id)
                                    addTo = null
                                    scope.launch { snackbar.showSnackbar("Añadida a «${p.name}»") }
                                }.padding(vertical = 12.dp, horizontal = 6.dp),
                        ) { Text(p.name, style = T.body, color = c.text) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addTo = null }) { Text("Cerrar") } },
        )
    }

    editing?.let { t ->
        TextInputDialog(
            title = "Editar etiquetas",
            initial = t.title,
            label = "Título",
            initial2 = t.artist,
            label2 = "Artista",
            confirm = "Guardar",
            onDismiss = { editing = null },
            onConfirm = {},
            onConfirm2 = { title, artist ->
                vm.updateTags(t.id, title, artist)
                editing = null
            },
        )
    }
}

@Composable
private fun PlaylistCard(
    p: PlaylistSummary,
    onClick: () -> Unit,
) {
    val c = LocalBruma.current
    Column(
        Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
    ) {
        Thumb(p.cover?.let { File(it) }, p.name, Modifier.size(104.dp), corner = 16.dp, icon = if (p.cover == null) BI.Music else null)
        Spacer(Modifier.height(6.dp))
        Text(p.name, style = T.small, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${p.itemCount} elementos", style = T.tiny, color = c.muted)
    }
}

@Composable
fun TrackRow(
    t: TrackEntity,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    highlight: Boolean = false,
) {
    val c = LocalBruma.current
    var menu by remember { mutableStateOf(false) }
    ListRow(
        title = t.title,
        subtitle =
            listOf(
                if (t.isVideo) "Vídeo" else t.artist.ifBlank { "Audio" },
                t.qualityLabel,
                if (t.durationSec > 0) fmtDuration(t.durationSec.toLong()) else "",
            ).filter { it.isNotBlank() }.joinToString(" · "),
        leading = {
            Thumb(t.coverPath?.let { File(it) }, t.title, Modifier.size(42.dp), corner = 10.dp, icon = if (t.isVideo) BI.Play else null)
        },
        onClick = onClick,
        highlight = highlight,
        trailing = {
            extra?.invoke()
            if (onAddToPlaylist != null || onEdit != null || onShare != null || onDelete != null) {
                Box {
                    Icon(
                        BI.More,
                        "Opciones",
                        tint = c.muted,
                        modifier =
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { menu = true }
                                .padding(8.dp),
                    )
                    DropdownMenu(menu, { menu = false }) {
                        if (onAddToPlaylist !=
                            null
                        ) {
                            DropdownMenuItem(text = {
                                Text(
                                    "Añadir a una lista",
                                )
                            }, leadingIcon = { Icon(BI.PlaylistAdd, null) }, onClick = {
                                menu =
                                    false
                                ; onAddToPlaylist()
                            })
                        }
                        if (onEdit !=
                            null
                        ) {
                            DropdownMenuItem(text = { Text("Editar etiquetas") }, leadingIcon = { Icon(BI.Edit, null) }, onClick = {
                                menu =
                                    false
                                ; onEdit()
                            })
                        }
                        if (onShare !=
                            null
                        ) {
                            DropdownMenuItem(text = { Text("Compartir") }, leadingIcon = { Icon(BI.Share, null) }, onClick = {
                                menu =
                                    false
                                ; onShare()
                            })
                        }
                        if (onDelete !=
                            null
                        ) {
                            DropdownMenuItem(text = { Text("Borrar del teléfono") }, leadingIcon = { Icon(BI.Trash, null) }, onClick = {
                                menu =
                                    false
                                ; onDelete()
                            })
                        }
                    }
                }
            }
        },
    )
}
