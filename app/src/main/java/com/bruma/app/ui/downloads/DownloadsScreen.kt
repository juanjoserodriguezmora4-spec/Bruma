package com.bruma.app.ui.downloads

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bruma.app.container
import com.bruma.app.data.db.DownloadEntity
import com.bruma.app.data.db.DownloadState
import com.bruma.app.data.db.TrackEntity
import com.bruma.app.data.db.st
import com.bruma.app.ui.common.BI
import com.bruma.app.ui.common.EmptyState
import com.bruma.app.ui.common.GlassBackground
import com.bruma.app.ui.common.IconChip
import com.bruma.app.ui.common.ProgressLine
import com.bruma.app.ui.common.Segmented
import com.bruma.app.ui.common.Thumb
import com.bruma.app.ui.common.fmtBytes
import com.bruma.app.ui.common.fmtEta
import com.bruma.app.ui.common.glass
import com.bruma.app.ui.theme.LocalBruma
import com.bruma.app.ui.theme.T
import kotlinx.coroutines.launch

private val ACTIVE =
    setOf(
        DownloadState.QUEUED,
        DownloadState.ANALYZING,
        DownloadState.DOWNLOADING,
        DownloadState.PROCESSING,
        DownloadState.PAUSED,
    )

@Composable
fun DownloadsScreen(onOpenTrack: (List<TrackEntity>, Int) -> Unit) {
    val c = LocalBruma.current
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val all by app.downloads.all.collectAsState(initial = emptyList())
    val tracks by app.library.tracks.collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val active = all.filter { it.st in ACTIVE }
    val done = all.filter { it.st == DownloadState.COMPLETED }
    val failed = all.filter { it.st == DownloadState.FAILED }
    val list =
        when (tab) {
            0 -> active
            1 -> done
            else -> failed
        }
    val anyRunning = active.any { it.st != DownloadState.PAUSED }

    GlassBackground(seed = 1) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Descargas", style = T.h1, color = c.text, modifier = Modifier.weight(1f))
                    if (tab == 0 && active.isNotEmpty()) {
                        IconChip(
                            icon = if (anyRunning) BI.Pause else BI.Play,
                            description = if (anyRunning) "Pausar todo" else "Reanudar todo",
                            onClick = { scope.launch { if (anyRunning) app.downloads.pauseAll() else app.downloads.resumeAll() } },
                        )
                    }
                    if (tab == 1 && done.isNotEmpty()) {
                        Text(
                            "Limpiar",
                            style = T.label,
                            color = c.accent,
                            modifier =
                                Modifier
                                    .clickable { scope.launch { app.downloads.clearCompleted() } }
                                    .padding(6.dp),
                        )
                    }
                }
            }
            item {
                Segmented(
                    listOf("En curso · ${active.size}", "Listas", "Fallidas" + if (failed.isNotEmpty()) " · ${failed.size}" else ""),
                    tab,
                    { tab = it },
                )
            }
            if (list.isEmpty()) {
                item {
                    when (tab) {
                        0 -> EmptyState(BI.Download, "Nada descargando", "Pega un enlace en Inicio para empezar.")
                        1 -> EmptyState(BI.Check, "Todavía no hay descargas listas", "Lo que termines aparecerá aquí y en tu biblioteca.")
                        else -> EmptyState(BI.Shield, "Sin errores", "Si una descarga falla, la verás aquí para reintentarla.")
                    }
                }
            }
            items(list, key = { it.id }) { d ->
                DownloadCard(
                    d = d,
                    onPause = { scope.launch { app.downloads.pause(d.id) } },
                    onResume = { scope.launch { app.downloads.resume(d.id) } },
                    onCancel = { scope.launch { app.downloads.cancel(d.id) } },
                    onRetry = { scope.launch { app.downloads.retry(d.id) } },
                    onRemove = { scope.launch { app.downloads.remove(d.id) } },
                    onOpen = {
                        val t = tracks.firstOrNull { it.id == d.trackId }
                        if (t != null) onOpenTrack(listOf(t), 0)
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadCard(
    d: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = LocalBruma.current
    val st = d.st
    val isError =
        st == DownloadState.FAILED ||
            (st == DownloadState.PAUSED && d.errorType != null && d.errorType != "USER" && d.errorType != "SYSTEM")
    val color =
        when {
            st == DownloadState.FAILED -> c.err
            st == DownloadState.PAUSED && isError -> c.err
            st == DownloadState.PAUSED -> c.warn
            st == DownloadState.COMPLETED -> c.ok
            else -> c.accent
        }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .glass(shape)
            .then(if (isError) Modifier.border(1.dp, c.err.copy(alpha = 0.35f), shape) else Modifier)
            .clickable(enabled = st == DownloadState.COMPLETED) { onOpen() }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(d.thumbnail, d.title, Modifier.size(44.dp), icon = if (d.kind == "VIDEO") BI.Video else BI.Music)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, style = T.title, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val q =
                    when {
                        d.quality <= 0 -> ""
                        d.kind == "VIDEO" -> " · ${d.quality}p"
                        else -> " · ${d.quality} kbps"
                    }
                val status =
                    when (st) {
                        DownloadState.QUEUED -> "En cola"
                        DownloadState.ANALYZING -> "Analizando…"
                        DownloadState.PROCESSING -> d.message ?: "Uniendo y añadiendo carátula…"
                        DownloadState.PAUSED, DownloadState.FAILED -> d.message ?: "En pausa"
                        DownloadState.COMPLETED -> "Listo · ${fmtBytes(d.totalBytes)}"
                        else -> d.message ?: "${d.container}$q${d.platform?.let { " · $it" } ?: ""}"
                    }
                Text(
                    if (st == DownloadState.DOWNLOADING &&
                        d.message == null
                    ) {
                        "${d.container}$q${d.platform?.let { " · $it" } ?: ""}"
                    } else {
                        status
                    },
                    style = T.tiny,
                    color =
                        when {
                            isError -> c.err
                            st == DownloadState.PAUSED -> c.warn
                            st == DownloadState.COMPLETED -> c.ok
                            else -> c.muted
                        },
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(8.dp))
            when (st) {
                DownloadState.DOWNLOADING, DownloadState.QUEUED, DownloadState.ANALYZING, DownloadState.PROCESSING ->
                    IconChip(BI.Pause, onPause, size = 30.dp, description = "Pausar")
                DownloadState.PAUSED -> IconChip(BI.Play, onResume, tint = c.accent, size = 30.dp, description = "Reanudar")
                DownloadState.FAILED -> IconChip(BI.Retry, onRetry, tint = c.accent, size = 30.dp, description = "Reintentar")
                DownloadState.COMPLETED -> IconChip(BI.Play, onOpen, tint = c.accent, size = 30.dp, description = "Reproducir")
                else -> {}
            }
            Spacer(Modifier.width(6.dp))
            when (st) {
                DownloadState.COMPLETED, DownloadState.FAILED ->
                    IconChip(
                        BI.Trash,
                        onRemove,
                        tint = c.muted,
                        size = 30.dp,
                        description = "Quitar de la lista",
                    )
                else -> IconChip(BI.Close, onCancel, tint = c.muted, size = 30.dp, description = "Cancelar")
            }
        }
        if (st != DownloadState.COMPLETED && st != DownloadState.FAILED) {
            Spacer(Modifier.height(10.dp))
            ProgressLine(if (st == DownloadState.PROCESSING) 1f else d.progress, color)
            if (st == DownloadState.DOWNLOADING) {
                Spacer(Modifier.height(5.dp))
                Row {
                    Text(
                        if (d.totalBytes >
                            0
                        ) {
                            "${fmtBytes((d.totalBytes * d.progress).toLong())} / ${fmtBytes(d.totalBytes)}"
                        } else {
                            "${(d.progress * 100).toInt()} %"
                        },
                        style = T.tiny,
                        color = c.muted,
                        modifier = Modifier.weight(1f),
                    )
                    val speed = if (d.speed > 0) "${fmtBytes(d.speed)}/s" else ""
                    val eta = fmtEta(d.eta)
                    Text(listOf(speed, eta).filter { it.isNotBlank() }.joinToString(" · "), style = T.tiny, color = c.muted)
                }
            }
        }
    }
}
