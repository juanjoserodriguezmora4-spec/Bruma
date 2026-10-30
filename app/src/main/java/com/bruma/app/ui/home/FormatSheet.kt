@file:OptIn(ExperimentalMaterial3Api::class)

package com.bruma.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.bruma.app.data.AppSettings
import com.bruma.app.download.Storage
import com.bruma.app.engine.MediaInfo
import com.bruma.app.ui.common.BI
import com.bruma.app.ui.common.Pill
import com.bruma.app.ui.common.PrimaryButton
import com.bruma.app.ui.common.RadioDot
import com.bruma.app.ui.common.Segmented
import com.bruma.app.ui.common.Thumb
import com.bruma.app.ui.common.fmtBytes
import com.bruma.app.ui.common.fmtDuration
import com.bruma.app.ui.theme.LocalBruma
import com.bruma.app.ui.theme.T

private val VIDEO_FORMATS = listOf("MP4", "WEBM", "M4V")
private val AUDIO_FORMATS = listOf("MP3", "M4A", "FLAC")

private fun bitratesFor(format: String) =
    when (format) {
        "MP3" -> listOf(320, 192, 128)
        "M4A" -> listOf(256, 160, 128)
        else -> emptyList()
    }

private fun qualityName(q: Int) =
    when (q) {
        1080 -> "1080p Full HD"
        720 -> "720p HD"
        480 -> "480p SD"
        360 -> "360p Móvil"
        else -> "Calidad original"
    }

@Composable
fun FormatSheet(
    info: MediaInfo?,
    batch: List<String>,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onDownload: (Choice) -> Unit,
) {
    val c = LocalBruma.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hasVideo = info?.hasVideo ?: true
    val qualities = info?.videoQualities?.takeIf { it.isNotEmpty() } ?: listOf(1080, 720, 480, 360)
    val isList = info?.isPlaylist == true || batch.isNotEmpty()
    val playlist = info?.takeIf { it.isPlaylist }

    var tab by remember { mutableIntStateOf(if (hasVideo) 0 else 1) }
    var quality by remember {
        mutableIntStateOf(qualities.firstOrNull { it <= settings.videoQuality && it != 0 } ?: qualities.first())
    }
    var vFormat by remember { mutableStateOf(settings.videoFormat) }
    var aFormat by remember { mutableStateOf(settings.audioFormat) }
    var aQuality by remember { mutableIntStateOf(settings.audioQuality) }
    var selected by remember { mutableStateOf(info?.entries?.indices?.toSet() ?: emptySet()) }
    var createPlaylist by remember { mutableStateOf(true) }

    val bitrates = bitratesFor(aFormat)
    if (bitrates.isNotEmpty() && aQuality !in bitrates) aQuality = bitrates.first()

    val duration = info?.durationSec ?: 0

    fun audioSize(kbps: Int): Long = if (aFormat == "FLAC") duration * 110_000L else kbps * duration * 125L
    val estimated: Long =
        when {
            isList -> 0L
            tab == 0 -> info?.videoSizes?.get(quality) ?: 0L
            else -> audioSize(aQuality)
        }
    val free = remember { Storage.freeBytes(context) }
    val notEnough = estimated > 0 && estimated * 1.1 > free
    val suggestion =
        if (notEnough && tab == 0) {
            qualities.firstOrNull { q -> q < quality && (info?.videoSizes?.get(q) ?: 0L) * 1.1 < free }
        } else {
            null
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (c.dark) Color(0xFF141926) else Color(0xFFF8F9FC),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.track),
            )
        },
    ) {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Encabezado
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Thumb(
                        model = info?.thumbnail,
                        key = info?.title ?: batch.firstOrNull() ?: "lote",
                        modifier = Modifier.size(width = 96.dp, height = 58.dp),
                        icon = if (info?.thumbnail == null) BI.Link else null,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                info != null -> info.title
                                else -> "${batch.size} enlaces"
                            },
                            style = T.title,
                            color = c.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val sub =
                            when {
                                playlist != null -> "Lista · ${playlist.entries.size} elementos"
                                info != null ->
                                    listOfNotNull(
                                        info.author,
                                        info.platform,
                                        if (duration > 0) fmtDuration(duration.toLong()) else null,
                                    ).joinToString(" · ")
                                else -> "Descarga por lotes"
                            }
                        Text(sub, style = T.tiny, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            item {
                if (hasVideo) {
                    Segmented(listOf("Vídeo", "Solo audio"), tab, { tab = it })
                } else {
                    Text("Este enlace solo tiene audio", style = T.small, color = c.muted)
                }
            }

            if (tab == 0) {
                item { Text("Calidad", style = T.small, color = c.muted, modifier = Modifier.padding(top = 8.dp)) }
                qualities.forEach { q ->
                    item(key = "q$q") {
                        val size = info?.videoSizes?.get(q) ?: 0L
                        OptionRow(
                            title = qualityName(q),
                            subtitle = if (q == qualities.firstOrNull { it <= 1080 }) "Recomendada" else null,
                            trailing = if (size > 0 && !isList) "~${fmtBytes(size)}" else null,
                            selected = quality == q,
                            onClick = { quality = q },
                        )
                    }
                }
                item { Text("Formato", style = T.small, color = c.muted, modifier = Modifier.padding(top = 6.dp)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VIDEO_FORMATS.forEach { f -> Pill(f, vFormat == f, { vFormat = f }) }
                    }
                }
            } else {
                item { Text("Formato", style = T.small, color = c.muted, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AUDIO_FORMATS.forEach { f -> Pill(f, aFormat == f, { aFormat = f }) }
                    }
                }
                if (bitrates.isNotEmpty()) {
                    item { Text("Tasa de bits", style = T.small, color = c.muted, modifier = Modifier.padding(top = 6.dp)) }
                    bitrates.forEach { b ->
                        item(key = "b$b") {
                            OptionRow(
                                title = "$b kbps",
                                subtitle =
                                    when (b) {
                                        bitrates.first() -> "Máxima calidad"
                                        bitrates.last() -> "Ocupa menos"
                                        else -> "Equilibrada"
                                    },
                                trailing = if (duration > 0 && !isList) "~${fmtBytes(b * duration * 125L)}" else null,
                                selected = aQuality == b,
                                onClick = { aQuality = b },
                            )
                        }
                    }
                }
                val src = info?.sourceAudioKbps ?: 0
                val warn =
                    when {
                        aFormat == "FLAC" && src in 1..400 ->
                            "El audio original viene con compresión (~$src kbps). FLAC lo guarda sin más pérdida, pero no lo hace sonar mejor."
                        bitrates.isNotEmpty() && src > 0 && aQuality > src + 16 ->
                            "La fuente viene a ~$src kbps. Elegir $aQuality kbps no mejora el sonido, solo pesa más."
                        else -> null
                    }
                if (warn != null) item { Notice(warn, c.warn) }
            }

            // Lista de reproducción: elegir elementos
            if (playlist != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            "Elementos (${selected.size}/${playlist.entries.size})",
                            style = T.small,
                            color = c.muted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (selected.size == playlist.entries.size) "Quitar todos" else "Elegir todos",
                            style = T.small,
                            color = c.accent,
                            modifier =
                                Modifier
                                    .clickable {
                                        selected =
                                            if (selected.size == playlist.entries.size) emptySet() else playlist.entries.indices.toSet()
                                    }.padding(4.dp),
                        )
                    }
                }
                itemsIndexed(playlist.entries, key = { i, _ -> "e$i" }) { i, e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selected = if (i in selected) selected - i else selected + i },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = i in selected,
                            onCheckedChange = { selected = if (it) selected + i else selected - i },
                            colors = CheckboxDefaults.colors(checkedColor = c.accent),
                        )
                        Text(
                            e.title,
                            style = T.small,
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (e.durationSec > 0) Text(fmtDuration(e.durationSec.toLong()), style = T.tiny, color = c.muted)
                    }
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { createPlaylist = !createPlaylist },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(createPlaylist, { createPlaylist = it }, colors = CheckboxDefaults.colors(checkedColor = c.accent))
                        Text("Crear la lista «${playlist.title}» en tu biblioteca", style = T.small, color = c.text)
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("Espacio libre: ${fmtBytes(free)}", style = T.tiny, color = c.muted, modifier = Modifier.weight(1f))
                    Text(
                        if (settings.embedMetadata) "Carátula y metadatos: Sí" else "Sin metadatos",
                        style = T.tiny,
                        color = c.muted,
                    )
                }
                if (notEnough) {
                    Spacer(Modifier.height(8.dp))
                    Notice(
                        if (suggestion != null) {
                            "No hay espacio suficiente para esta calidad. ${qualityName(suggestion)} sí cabe."
                        } else {
                            "No hay espacio suficiente. Libera espacio o elige solo audio."
                        },
                        c.err,
                        action = suggestion?.let { s -> "Usar ${s}p" to { quality = s } },
                    )
                }
            }

            item {
                val count =
                    when {
                        info?.isPlaylist == true -> selected.size
                        batch.isNotEmpty() -> batch.size
                        else -> 1
                    }
                val label =
                    when {
                        count > 1 || isList -> "Descargar $count elementos"
                        estimated > 0 -> "Descargar · ${fmtBytes(estimated)}"
                        else -> "Descargar"
                    }
                PrimaryButton(
                    text = label,
                    icon = BI.Download,
                    enabled = count > 0,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 12.dp),
                    onClick = {
                        val video = tab == 0
                        onDownload(
                            Choice(
                                video = video,
                                container = if (video) vFormat else aFormat,
                                quality =
                                    if (video) {
                                        quality
                                    } else if (aFormat == "FLAC") {
                                        0
                                    } else {
                                        aQuality
                                    },
                                estimated = estimated,
                                entries = info?.entries?.filterIndexed { i, _ -> i in selected } ?: emptyList(),
                                createPlaylist = createPlaylist,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String?,
    trailing: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalBruma.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) c.accent else Color.Transparent, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.title, color = c.text)
            if (subtitle != null) Text(subtitle, style = T.tiny, color = c.muted)
        }
        if (trailing != null) Text(trailing, style = T.small, color = c.muted)
    }
}

@Composable
fun Notice(
    text: String,
    color: Color,
    action: Pair<String, () -> Unit>? = null,
) {
    val c = LocalBruma.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BI.Alert, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = T.small, color = c.text, modifier = Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Text(action.first, style = T.label, color = c.accent, modifier = Modifier.clickable { action.second() }.padding(4.dp))
        }
    }
}
