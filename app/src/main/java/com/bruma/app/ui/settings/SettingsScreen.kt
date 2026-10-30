package com.bruma.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import com.bruma.app.BuildConfig
import com.bruma.app.container
import com.bruma.app.data.AppSettings
import com.bruma.app.data.SettingsRepository
import com.bruma.app.engine.Engine
import com.bruma.app.ui.common.BI
import com.bruma.app.ui.common.ChoiceDialog
import com.bruma.app.ui.common.GlassBackground
import com.bruma.app.ui.common.Segmented
import com.bruma.app.ui.common.TextInputDialog
import com.bruma.app.ui.common.glass
import com.bruma.app.ui.theme.LocalBruma
import com.bruma.app.ui.theme.PALETTES
import com.bruma.app.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Picker(
    val title: String,
    val options: List<String>,
    val selected: Int,
    val onPick: (Int) -> Unit,
)

private val AUDIO_DEFAULTS = listOf("MP3" to 320, "MP3" to 192, "MP3" to 128, "M4A" to 256, "M4A" to 160, "M4A" to 128, "FLAC" to 0)

private fun audioName(p: Pair<String, Int>) = if (p.first == "FLAC") "FLAC · sin pérdida" else "${p.first} · ${p.second} kbps"

@Composable
fun SettingsScreen() {
    val c = LocalBruma.current
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    val s by app.settings.flow.collectAsState(initial = AppSettings())
    var picker by remember { mutableStateOf<Picker?>(null) }
    var editFolder by remember { mutableStateOf(false) }
    var showLegal by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var engineStatus by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        version =
            withContext(Dispatchers.IO) {
                runCatching {
                    Engine.ensureInit(context)
                    Engine.version(context)
                }.getOrNull()
            }
    }

    fun <V> set(
        key: Preferences.Key<V>,
        value: V,
    ) {
        scope.launch { app.settings.set(key, value) }
    }

    GlassBackground(seed = 2) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Ajustes", style = T.h1, color = c.text) }

            item { GroupTitle("DESCARGAS") }
            item {
                Group {
                    ValueRow(BI.Video, "Calidad de vídeo por defecto", "${s.videoQuality}p") {
                        val opts = listOf(1080, 720, 480, 360)
                        picker =
                            Picker("Calidad de vídeo", opts.map { "${it}p" }, opts.indexOf(s.videoQuality)) {
                                set(SettingsRepository.VIDEO_QUALITY, opts[it])
                            }
                    }
                    Divider()
                    ValueRow(BI.Video, "Formato de vídeo", s.videoFormat) {
                        val opts = listOf("MP4", "WEBM", "M4V")
                        picker =
                            Picker("Formato de vídeo", opts, opts.indexOf(s.videoFormat)) { set(SettingsRepository.VIDEO_FORMAT, opts[it]) }
                    }
                    Divider()
                    val current = s.audioFormat to (if (s.audioFormat == "FLAC") 0 else s.audioQuality)
                    ValueRow(BI.Music, "Audio por defecto", audioName(current)) {
                        picker =
                            Picker("Audio por defecto", AUDIO_DEFAULTS.map(::audioName), AUDIO_DEFAULTS.indexOf(current)) {
                                val (f, q) = AUDIO_DEFAULTS[it]
                                set(SettingsRepository.AUDIO_FORMAT, f)
                                if (q > 0) set(SettingsRepository.AUDIO_QUALITY, q)
                            }
                    }
                    Divider()
                    ValueRow(BI.Download, "Descargas simultáneas", s.maxConcurrent.toString()) {
                        val opts = (1..5).toList()
                        picker =
                            Picker("Descargas al mismo tiempo", opts.map { it.toString() }, opts.indexOf(s.maxConcurrent)) {
                                set(SettingsRepository.MAX_CONCURRENT, opts[it])
                            }
                    }
                    Divider()
                    ToggleRow(BI.Wifi, "Solo con Wi-Fi", "No gastar datos móviles", s.wifiOnly) { set(SettingsRepository.WIFI_ONLY, it) }
                    Divider()
                    ToggleRow(
                        BI.Alert,
                        "Pausar con batería baja",
                        null,
                        s.pauseLowBattery,
                    ) { set(SettingsRepository.PAUSE_LOW_BATTERY, it) }
                }
            }

            item { GroupTitle("BIBLIOTECA") }
            item {
                Group {
                    ValueRow(BI.Folder, "Carpeta de guardado", "Música/${s.folder}") { editFolder = true }
                    Divider()
                    ToggleRow(BI.Music, "Incrustar carátula y metadatos", "Título, artista y portada dentro del archivo", s.embedMetadata) {
                        set(SettingsRepository.EMBED_METADATA, it)
                    }
                    Divider()
                    ToggleRow(
                        BI.Edit,
                        "Carátula cuadrada en audio",
                        "Recorta la miniatura del vídeo",
                        s.cropCover,
                    ) { set(SettingsRepository.CROP_COVER, it) }
                    Divider()
                    ToggleRow(BI.Paste, "Detectar enlaces copiados", "Lee el portapapeles solo con la app abierta", s.clipboard) {
                        set(SettingsRepository.CLIPBOARD, it)
                    }
                }
            }

            item { GroupTitle("APARIENCIA") }
            item {
                val idx =
                    when (s.theme) {
                        "LIGHT" -> 0
                        "DARK" -> 1
                        else -> 2
                    }
                Segmented(listOf("Claro", "Oscuro", "Sistema"), idx, {
                    set(SettingsRepository.THEME, listOf("LIGHT", "DARK", "SYSTEM")[it])
                })
            }
            item {
                PalettePicker(selected = s.palette) { set(SettingsRepository.PALETTE, it) }
            }
            item {
                Group {
                    ToggleRow(BI.Shield, "Reducir transparencia", "Paneles sólidos en lugar de vidrio", s.reduceTransparency) {
                        set(SettingsRepository.REDUCE_TRANSPARENCY, it)
                    }
                }
            }

            item { GroupTitle("MOTOR DE EXTRACCIÓN") }
            item {
                Group {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(BI.Retry, null, tint = c.muted, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("yt-dlp", style = T.title, color = c.text)
                            Text(
                                engineStatus ?: ("Versión " + (version ?: s.engineVersion.ifBlank { "—" })),
                                style = T.tiny,
                                color = c.muted,
                            )
                        }
                        if (updating) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = c.accent, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                "Actualizar",
                                style = T.label,
                                color = c.accent,
                                modifier =
                                    Modifier
                                        .clickable {
                                            updating = true
                                            engineStatus = "Buscando versión nueva…"
                                            scope.launch {
                                                engineStatus =
                                                    try {
                                                        val (status, v) = Engine.update(context)
                                                        version = v
                                                        if (v != null) app.settings.set(SettingsRepository.ENGINE_VERSION, v)
                                                        if (status == "ALREADY_UP_TO_DATE") "Ya tienes la última versión" else "Actualizado"
                                                    } catch (e: Exception) {
                                                        "No se pudo actualizar. Revisa tu conexión."
                                                    }
                                                updating = false
                                            }
                                        }.padding(6.dp),
                            )
                        }
                    }
                }
            }

            item { GroupTitle("OTROS") }
            item {
                Group {
                    ValueRow(BI.Shield, "Permisos de la app", "") {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    Divider()
                    ValueRow(BI.Info, "Uso responsable", "") { showLegal = true }
                    Divider()
                    ValueRow(BI.Info, "Versión de Bruma", BuildConfig.VERSION_NAME, chevron = false) {}
                }
            }
        }
    }

    picker?.let { p ->
        ChoiceDialog(p.title, p.options, p.selected, { picker = null }) { i ->
            p.onPick(i)
            picker = null
        }
    }
    if (editFolder) {
        TextInputDialog(
            title = "Carpeta de guardado",
            initial = s.folder,
            label = "Nombre de la carpeta",
            confirm = "Guardar",
            onDismiss = { editFolder = false },
            onConfirm = {
                set(SettingsRepository.FOLDER, it.trim().replace("/", "").ifBlank { "Bruma" })
                editFolder = false
            },
        )
    }
    if (showLegal) LegalDialog { showLegal = false }
}

@Composable
fun LegalDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uso responsable", style = T.h2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Descarga solo contenido propio, de dominio público, con licencia libre (por ejemplo Creative Commons) " +
                        "o que tengas permiso de guardar. Algunas plataformas, como YouTube, no permiten descargar fuera de sus " +
                        "propias funciones; respetar sus condiciones es tu responsabilidad.\n\n" +
                        "Bruma no rompe protecciones DRM, no descarga de servicios de suscripción y no tiene funciones para " +
                        "volver a publicar archivos.\n\n" +
                        "Privacidad: no hay cuentas, no hay anuncios ni rastreadores, y tu historial de descargas nunca sale del teléfono.",
                    style = T.body,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } },
    )
}

/** Fila de círculos con las paletas de color. Cada círculo muestra las manchas del fondo y el acento al centro. */
@Composable
private fun PalettePicker(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = LocalBruma.current
    Column(
        Modifier
            .fillMaxWidth()
            .glass(
                androidx.compose.foundation.shape
                    .RoundedCornerShape(18.dp),
            ).padding(vertical = 14.dp),
    ) {
        Text("Color del tema", style = T.body, color = c.text, modifier = Modifier.padding(horizontal = 14.dp))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(PALETTES, key = { it.id }) { p ->
                val on = p.id == selected
                val blobs = if (c.dark) p.blobsDark else p.blobsLight
                val accent = if (c.dark) p.accentDark else p.accentLight
                Column(
                    Modifier
                        .width(58.dp)
                        .clip(
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(12.dp),
                        ).clickable { onSelect(p.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .border(if (on) 2.5.dp else 1.dp, if (on) accent else c.track, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(blobs)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) {
                                Icon(
                                    BI.Check,
                                    null,
                                    tint = if (accent.luminance() > 0.4f) Color(0xFF121826) else Color.White,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        p.name,
                        style = T.tiny,
                        color = if (on) c.accent else c.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    val c = LocalBruma.current
    Text(text, style = T.overline, color = c.muted, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(
                androidx.compose.foundation.shape
                    .RoundedCornerShape(18.dp),
            ),
    ) { content() }
}

@Composable
private fun Divider() {
    val c = LocalBruma.current
    HorizontalDivider(color = c.track, modifier = Modifier.padding(start = 45.dp))
}

@Composable
private fun ValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalBruma.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.muted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = T.body, color = c.text, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) Text(value, style = T.small, color = c.muted)
        if (chevron) Icon(BI.Chevron, null, tint = c.muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = LocalBruma.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.muted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.body, color = c.text)
            if (subtitle != null) Text(subtitle, style = T.tiny, color = c.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = c.accent,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = c.track,
                    uncheckedBorderColor = Color.Transparent,
                ),
        )
    }
    Spacer(Modifier.height(0.dp))
}
