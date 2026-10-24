package com.bruma.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import com.bruma.app.ui.common.BI
import com.bruma.app.ui.common.Badge
import com.bruma.app.ui.common.GlassBackground
import com.bruma.app.ui.common.IconChip
import com.bruma.app.ui.common.PrimaryButton
import com.bruma.app.ui.common.ProgressLine
import com.bruma.app.ui.common.SectionHeader
import com.bruma.app.ui.common.Thumb
import com.bruma.app.ui.common.fmtBytes
import com.bruma.app.ui.common.fmtDuration
import com.bruma.app.ui.common.glass
import com.bruma.app.ui.theme.LocalBruma
import com.bruma.app.ui.theme.T
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

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
    val ui by vm.ui.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

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
                        analyzing = ui.analyzing,
                        onText = vm::setText,
                        onPaste = {
                            val t = clipboard.getText()?.text.orEmpty()
                            if (t.isNotBlank()) {
                                vm.setText(t)
                                vm.analyze(t)
                            }
                        },
                        onGo = { vm.analyze() },
                    )
                    if (ui.error != null) {
                        Text(ui.error ?: "", style = T.small, color = c.err, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                    }
                }
            }

            val clip = ui.clip
            if (clip != null && ui.info == null) {
                item { ClipCard(url = clip, info = ui.clipInfo, onDismiss = vm::dismissClip, onGo = { vm.analyze(clip) }) }
            }

            if (active.isNotEmpty()) {
                item { SectionHeader("En curso", action = "Ver todo", onAction = onOpenDownloads) }
                active.take(2).forEach { d -> item(key = "d${d.id}") { MiniDownload(d, onOpenDownloads) } }
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
            onDownload = { choice -> gate { vm.download(choice) } },
        )
    }
}

@Composable
private fun UrlBar(
    text: String,
    analyzing: Boolean,
    onText: (String) -> Unit,
    onPaste: () -> Unit,
    onGo: () -> Unit,
) {
    val c = LocalBruma.current
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BI.Link, null, tint = c.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = text,
            onValueChange = onText,
            singleLine = true,
            textStyle = T.body.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Pega un enlace…", style = T.body, color = c.muted)
                inner()
            },
        )
        Spacer(Modifier.width(8.dp))
        when {
            analyzing -> CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            text.isNotBlank() ->
                Text(
                    "Analizar",
                    style = T.label,
                    color = c.accent,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onGo() }
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
