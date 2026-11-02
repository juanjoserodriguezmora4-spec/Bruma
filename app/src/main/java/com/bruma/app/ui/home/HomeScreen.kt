@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.bruma.app.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bruma.app.container
import com.bruma.app.data.SettingsRepository
import com.bruma.app.data.db.DownloadEntity
import com.bruma.app.data.db.DownloadState
import com.bruma.app.data.db.TrackEntity
import com.bruma.app.data.db.st
import com.bruma.app.engine.SearchKind
import com.bruma.app.engine.SearchPlatform
import com.bruma.app.engine.SearchResult
import com.bruma.app.ui.common.BI
import com.bruma.app.ui.common.Badge
import com.bruma.app.ui.common.GlassBackground
import com.bruma.app.ui.common.IconChip
import com.bruma.app.ui.common.LocalSnackbar
import com.bruma.app.ui.common.Pill
import com.bruma.app.ui.common.PrimaryButton
import com.bruma.app.ui.common.ProgressLine
import com.bruma.app.ui.common.SectionHeader
import com.bruma.app.ui.common.Segmented
import com.bruma.app.ui.common.Thumb
import com.bruma.app.ui.common.fmtBytes
import com.bruma.app.ui.common.fmtDuration
import com.bruma.app.ui.common.glass
import com.bruma.app.ui.theme.LocalBruma
import com.bruma.app.ui.theme.T
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
    incoming: String?,
    onIncomingConsumed: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenTrack: (List<TrackEntity>, Int) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val c = LocalBruma.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbar.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val discover by vm.discover.collectAsStateWithLifecycle()

    // Las recomendaciones se cargan al abrir Inicio (y se reutilizan durante 2 horas).
    LaunchedEffect(Unit) { vm.loadDiscover() }

    val gate =
        rememberPermissionGate(settings.notifAsked) {
            scope.launch { context.container.settings.set(SettingsRepository.NOTIF_ASKED, true) }
        }

    LaunchedEffect(incoming) {
        if (incoming != null) {
            vm.setText(incoming)
            vm.analyze(incoming)
            onIncomingConsumed()
        }
    }

    // El botón "atrás" del celular cierra la búsqueda antes de salir de la app.
    BackHandler(enabled = ui.searchQuery != null) { vm.clearSearch() }

    LifecycleResumeEffect(Unit) {
        runCatching { vm.onClipboard(clipboard.getText()?.text) }
        onPauseOrDispose { }
    }

    GlassBackground {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        Text(
                            when (hour) {
                                in 5..11 -> "Buenos días"
                                in 12..18 -> "Buenas tardes"
                                else -> "Buenas noches"
                            },
                            style = T.small,
                            color = c.muted,
                        )
                        Text("Bruma", style = T.h1, color = c.text)
                    }
                    IconChip(
                        icon = if (c.dark) BI.Sun else BI.Moon,
                        description = "Cambiar tema",
                        onClick = {
                            scope.launch {
                                context.container.settings.set(SettingsRepository.THEME, if (c.dark) "LIGHT" else "DARK")
                            }
                        },
                    )
                }
            }

            item {
                Column {
                    UrlBar(
                        text = ui.text,
                        analyzing = ui.analyzing || ui.searching,
                        showClear = ui.text.isNotEmpty() || ui.searchQuery != null,
                        onText = vm::setText,
                        onPaste = {
                            val t = clipboard.getText()?.text.orEmpty()
                            if (t.isNotBlank()) {
                                vm.setText(t)
                                vm.submit(t)
                            }
                        },
                        onGo = { vm.submit() },
                        onClear = vm::clearSearch,
                    )
                    if (ui.error != null) {
                        Text(ui.error ?: "", style = T.small, color = c.err, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                    }
                }
            }

            // ---------------- Resultados de búsqueda
            if (ui.searchQuery != null) {
                item {
                    Segmented(
                        SearchPlatform.entries.map { it.label },
                        SearchPlatform.entries.indexOf(ui.platform),
                        { vm.setPlatform(SearchPlatform.entries[it]) },
                    )
                }
                if (ui.platform == SearchPlatform.YOUTUBE) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SearchKind.entries.forEach { k -> Pill(k.label, ui.kind == k, { vm.setKind(k) }) }
                        }
                    }
                }
                // Barra de selección múltiple: se queda pegada arriba mientras bajas por la lista.
                if (!ui.searching && ui.results.isNotEmpty() && ui.kind == SearchKind.SONGS) {
                    stickyHeader(key = "selbar") {
                        SelectionBar(
                            count = ui.results.size,
                            selecting = ui.selecting,
                            selected = ui.selected.size,
                            onToggle = vm::toggleSelecting,
                            onAll = vm::selectAll,
                            onDownload = vm::downloadSelected,
                        )
                    }
                }
                when {
                    ui.searching ->
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Buscando en ${ui.platform.label}…", style = T.small, color = c.muted)
                            }
                        }
                    ui.searchError != null && ui.results.isEmpty() ->
                        item {
                            Text(
                                ui.searchError ?: "",
                                style = T.small,
                                color = c.muted,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .glass()
                                        .padding(18.dp),
                            )
                        }
                    else -> {
                        if (ui.searchError != null) {
                            item { Text(ui.searchError ?: "", style = T.small, color = c.err, modifier = Modifier.padding(start = 6.dp)) }
                        }
                        items(ui.results, key = { "s" + it.url }) { r ->
                            val selectable = ui.selecting && !r.isPlaylist
                            ResultRow(
                                r,
                                loading = ui.openingUrl == r.url,
                                checked = if (selectable) r.url in ui.selected else null,
                            ) {
                                if (selectable) vm.toggleSelected(r.url) else vm.openResult(r)
                            }
                        }
                        // Al llegar al final se piden más resultados solos.
                        if (ui.nextToken != null) {
                            item(key = "more") {
                                LaunchedEffect(ui.results.size) { vm.loadMore() }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Cargando más…", style = T.small, color = c.muted)
                                }
                            }
                        }
                    }
                }
                return@LazyColumn
            }

            val clip = ui.clip
            if (clip != null && ui.info == null) {
                item { ClipCard(url = clip, info = ui.clipInfo, onDismiss = vm::dismissClip, onGo = { vm.analyze(clip) }) }
            }

            if (active.isNotEmpty()) {
                item { SectionHeader("En curso", action = "Ver todo", onAction = onOpenDownloads) }
                active.take(2).forEach { d -> item(key = "d${d.id}") { MiniDownload(d, onOpenDownloads) } }
            }

            // ---------------- Descubrir
            item(key = "disc-head") {
                SectionHeader(
                    "Descubrir",
                    action = if (discover.loading) null else "Actualizar",
                    onAction = { vm.loadDiscover(force = true) },
                )
            }
            if (discover.loading && discover.sections.isEmpty()) {
                item(key = "disc-loading") { RecoSkeleton() }
            }
            discover.error?.let { msg ->
                item(key = "disc-error") { Text(msg, style = T.small, color = c.muted, modifier = Modifier.padding(start = 6.dp)) }
            }
            discover.sections.forEach { sec ->
                item(key = "disc-" + sec.id) {
                    RecoSection(
                        title = sec.title,
                        subtitle = sec.subtitle,
                        songs = sec.items,
                        loadingUrl = ui.openingUrl,
                        onOpen = { vm.openResult(it, SearchPlatform.YOUTUBE) },
                        onAll = { vm.startBatch(sec.items) },
                    )
                }
            }
            item(key = "disc-genres") {
                Column {
                    Text("Explora por género", style = T.small, color = c.muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(discover.genres, key = { it }) { g ->
                            Pill(g, discover.selectedGenre == g, { vm.selectGenre(g) })
                        }
                    }
                }
            }
            discover.selectedGenre?.let { g ->
                item(key = "disc-genre-items") {
                    if (discover.genreLoading) {
                        RecoSkeleton()
                    } else if (discover.genreItems.isNotEmpty()) {
                        RecoSection(
                            title = "Lo mejor de $g",
                            subtitle = null,
                            songs = discover.genreItems,
                            loadingUrl = ui.openingUrl,
                            onOpen = { vm.openResult(it, SearchPlatform.YOUTUBE) },
                            onAll = { vm.startBatch(discover.genreItems) },
                        )
                    }
                }
            }

            item { SectionHeader("Recientes") }
            if (recent.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .glass()
                            .padding(18.dp),
                    ) {
                        Text("Tu biblioteca está vacía", style = T.title, color = c.text)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Copia un enlace de YouTube, Vimeo, SoundCloud o cualquier página con vídeo y pégalo arriba. " +
                                "También puedes tocar «Compartir» en otra app y elegir Bruma.",
                            style = T.small,
                            color = c.muted,
                        )
                    }
                }
            } else {
                recent.chunked(2).forEach { pair ->
                    item(key = "r${pair.first().id}") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { t ->
                                RecentCard(t, Modifier.weight(1f)) { onOpenTrack(listOf(t), 0) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (ui.info != null || ui.batch.isNotEmpty()) {
        FormatSheet(
            info = ui.info,
            batch = ui.batch,
            settings = settings,
            onDismiss = vm::closeSheet,
            loading = ui.loadingFormats,
            loadError = ui.formatsError,
            onDownload = { choice ->
                gate {
                    vm.download(choice)
                    scope.launch { snackbar.showSnackbar("Se agregó a Descargas") }
                }
            },
        )
    }
}

@Composable
private fun UrlBar(
    text: String,
    analyzing: Boolean,
    showClear: Boolean,
    onText: (String) -> Unit,
    onPaste: () -> Unit,
    onGo: () -> Unit,
    onClear: () -> Unit,
) {
    val c = LocalBruma.current
    val isLink = extractUrls(text).isNotEmpty()
    val keyboard = LocalSoftwareKeyboardController.current
    val go = {
        keyboard?.hide()
        onGo()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (text.isBlank() || isLink) BI.Link else BI.Search, null, tint = c.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = text,
            onValueChange = onText,
            singleLine = true,
            textStyle = T.body.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { go() }, onGo = { go() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Pega un enlace o busca…", style = T.body, color = c.muted, maxLines = 1)
                inner()
            },
        )
        if (showClear && !analyzing) {
            Icon(
                BI.Close,
                "Borrar",
                tint = c.muted,
                modifier =
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClear() }
                        .padding(6.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        when {
            analyzing -> CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            text.isNotBlank() ->
                Text(
                    if (isLink) "Analizar" else "Buscar",
                    style = T.label,
                    color = c.accent,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { go() }
                            .padding(4.dp),
                )
            else ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPaste() }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BI.Paste, null, tint = c.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pegar", style = T.label, color = c.accent)
                }
        }
    }
}

@Composable
private fun ClipCard(
    url: String,
    info: com.bruma.app.engine.MediaInfo?,
    onDismiss: () -> Unit,
    onGo: () -> Unit,
) {
    val c = LocalBruma.current
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(info?.thumbnail, info?.title ?: url, Modifier.size(width = 64.dp, height = 42.dp), icon = BI.Play)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Enlace detectado en el portapapeles", style = T.tiny, color = c.muted)
                Text(info?.title ?: url, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    info?.platform?.let { Badge(it) }
                    if ((info?.durationSec ?: 0) > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(fmtDuration((info?.durationSec ?: 0).toLong()), style = T.tiny, color = c.muted)
                    }
                    if (info == null) Text("Analizando…", style = T.tiny, color = c.muted)
                }
            }
            Icon(
                BI.Close,
                "Descartar",
                tint = c.muted,
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(5.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Elegir formato y descargar", onGo, Modifier.fillMaxWidth(), icon = BI.Download)
    }
}

@Composable
private fun MiniDownload(
    d: DownloadEntity,
    onClick: () -> Unit,
) {
    val c = LocalBruma.current
    Row(
        Modifier
            .fillMaxWidth()
            .glass()
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(d.thumbnail, d.title, Modifier.size(42.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(d.title, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                Text(
                    "${d.container}" + if (d.quality > 0) " · ${d.quality}${if (d.kind == "VIDEO") "p" else " kbps"}" else "",
                    style = T.tiny,
                    color = c.muted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when (d.st) {
                        DownloadState.PAUSED -> "En pausa"
                        DownloadState.PROCESSING -> "Procesando"
                        DownloadState.QUEUED -> "En cola"
                        else -> "${(d.progress * 100).toInt()} %" + if (d.speed > 0) " · ${fmtBytes(d.speed)}/s" else ""
                    },
                    style = T.tiny,
                    color = if (d.st == DownloadState.PAUSED) c.warn else c.muted,
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressLine(d.progress, if (d.st == DownloadState.PAUSED) c.warn else c.accent)
        }
    }
}

private fun fmtViews(v: Long): String =
    when {
        v <= 0 -> ""
        v >= 1_000_000_000 -> String.format(Locale("es", "CO"), "%.1f mil M vistas", v / 1e9)
        v >= 1_000_000 -> String.format(Locale("es", "CO"), "%.1f M vistas", v / 1e6)
        v >= 1_000 -> "${v / 1000} mil vistas"
        else -> "$v vistas"
    }

@Composable
private fun ResultRow(
    r: SearchResult,
    loading: Boolean,
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    val c = LocalBruma.current
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .clickable(enabled = !loading) { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 120.dp, height = 68.dp), contentAlignment = Alignment.Center) {
            Thumb(r.thumbnail, r.title, Modifier.fillMaxSize(), corner = 10.dp)
            if (loading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
            val label =
                when {
                    r.isPlaylist -> if (r.itemCount > 0) "${r.itemCount} canciones" else "Lista"
                    r.isLive -> "EN VIVO"
                    r.durationSec > 0 -> fmtDuration(r.durationSec.toLong())
                    else -> null
                }
            if (label != null) {
                Text(
                    label,
                    style = T.tiny,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (r.isLive) Color(0xDDE5484D) else Color(0xAA000000))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = T.title, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            val sub =
                listOfNotNull(
                    if (r.isPlaylist) "Álbum o lista" else null,
                    r.author,
                    fmtViews(r.views).ifBlank { null },
                ).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = T.tiny, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (loading) Text(if (r.isPlaylist) "Leyendo canciones…" else "Buscando formatos…", style = T.tiny, color = c.accent)
        }
        if (checked != null) {
            Spacer(Modifier.width(8.dp))
            Checkbox(checked = checked, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = c.accent))
        }
    }
}

/** Fila horizontal de canciones recomendadas, con botón para descargarlas todas. */
@Composable
private fun RecoSection(
    title: String,
    subtitle: String?,
    songs: List<SearchResult>,
    loadingUrl: String?,
    onOpen: (SearchResult) -> Unit,
    onAll: () -> Unit,
) {
    val c = LocalBruma.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = T.tiny, color = c.muted)
            }
            Text(
                "Descargar ${songs.size}",
                style = T.label,
                color = c.accent,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAll() }
                        .padding(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(songs, key = { it.url }) { r ->
                Column(
                    Modifier
                        .width(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpen(r) },
                ) {
                    Box(Modifier.size(width = 150.dp, height = 84.dp)) {
                        Thumb(r.thumbnail, r.title, Modifier.fillMaxSize(), corner = 12.dp)
                        if (loadingUrl == r.url) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x66000000)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (r.durationSec > 0) {
                            Text(
                                fmtDuration(r.durationSec.toLong()),
                                style = T.tiny,
                                color = Color.White,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xAA000000))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(r.title, style = T.small, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!r.author.isNullOrBlank()) {
                        Text(r.author, style = T.tiny, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Tarjetas vacías mientras cargan las recomendaciones. */
@Composable
private fun RecoSkeleton() {
    val c = LocalBruma.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Column(Modifier.width(150.dp)) {
                Box(
                    Modifier
                        .size(width = 150.dp, height = 84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.track),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .size(width = 120.dp, height = 10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(c.track),
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    selecting: Boolean,
    selected: Int,
    onToggle: () -> Unit,
    onAll: () -> Unit,
    onDownload: () -> Unit,
) {
    val c = LocalBruma.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .glass(RoundedCornerShape(16.dp), strong = true)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!selecting) {
            Text("$count resultados", style = T.small, color = c.muted, modifier = Modifier.weight(1f))
            Text(
                "Seleccionar varias",
                style = T.label,
                color = c.accent,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggle() }
                        .padding(6.dp),
            )
        } else {
            Icon(
                BI.Close,
                "Salir de la selección",
                tint = c.muted,
                modifier =
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggle() }
                        .padding(6.dp),
            )
            Text(
                "$selected elegidas",
                style = T.label,
                color = c.text,
                modifier = Modifier.padding(start = 4.dp),
            )
            Text(
                "Todas",
                style = T.label,
                color = c.accent,
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAll() }
                        .padding(6.dp),
            )
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                "Descargar",
                onDownload,
                icon = BI.Download,
                enabled = selected > 0,
            )
        }
    }
}

@Composable
private fun RecentCard(
    t: TrackEntity,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalBruma.current
    Column(
        modifier
            .glass()
            .clickable { onClick() }
            .padding(8.dp),
    ) {
        Thumb(
            t.coverPath?.let { File(it) },
            t.title,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f),
            icon = if (t.isVideo) BI.Play else null,
        )
        Spacer(Modifier.height(6.dp))
        Text(t.title, style = T.small, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.qualityLabel, style = T.tiny, color = c.muted, maxLines = 1)
    }
}
