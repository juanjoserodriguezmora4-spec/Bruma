package com.bruma.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bruma.app.ui.theme.LocalBruma
import com.bruma.app.ui.theme.LocalSolid
import com.bruma.app.ui.theme.T
import java.util.Locale
import kotlin.math.abs

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Panel de vidrio: blanco translúcido con borde fino. Con "reducir transparencia" se vuelve sólido. */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(20.dp),
    strong: Boolean = false,
): Modifier {
    val c = LocalBruma.current
    val solid = LocalSolid.current
    val bg =
        when {
            solid -> c.solid
            strong -> c.glassStrong
            else -> c.glass
        }
    return this
        .clip(shape)
        .background(bg, shape)
        .border(1.dp, if (solid) c.track else c.border, shape)
}

/** Fondo con manchas de color difuminadas detrás del vidrio. */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    seed: Int = 0,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalBruma.current
    val alpha = if (c.dark) 0.6f else 0.95f
    Box(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .drawBehind {
                val w = size.width
                val h = size.height
                val spots =
                    listOf(
                        Triple(0.05f, 0.04f, 0.85f),
                        Triple(1.02f, 0.30f, 0.80f),
                        Triple(-0.05f, 0.72f, 0.75f),
                        Triple(0.95f, 0.98f, 0.70f),
                    )
                spots.forEachIndexed { i, (x, y, r) ->
                    val color = c.blobs[(i + seed) % c.blobs.size]
                    val center = Offset(x * w, y * h)
                    val radius = r * w
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                                center = center,
                                radius = radius,
                            ),
                        radius = radius,
                        center = center,
                    )
                }
            },
        content = content,
    )
}

private val gradients =
    listOf(
        listOf(Color(0xFF7FA3FF), Color(0xFFB78CFF)),
        listOf(Color(0xFFFFB38A), Color(0xFFFF7BA9)),
        listOf(Color(0xFF8ED8C8), Color(0xFF6FA8FF)),
        listOf(Color(0xFFC8B2FF), Color(0xFFFFC4D8)),
        listOf(Color(0xFF3CCFB4), Color(0xFF4B7BFF)),
        listOf(Color(0xFF8A7BFF), Color(0xFFE07AB0)),
    )

fun gradientFor(key: String): List<Color> = gradients[abs(key.hashCode()) % gradients.size]

/** Miniatura o carátula; si no hay imagen muestra un degradado. */
@Composable
fun Thumb(
    model: Any?,
    key: String,
    modifier: Modifier,
    corner: Dp = 12.dp,
    icon: ImageVector? = null,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(gradientFor(key))),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBruma.current
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) c.selected else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = T.label, color = if (on) c.text else c.muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val c = LocalBruma.current
    val shape = RoundedCornerShape(50)
    val mod =
        if (selected) {
            Modifier
                .clip(shape)
                .background(c.accent, shape)
        } else {
            Modifier.glass(shape)
        }
    Box(
        mod
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = T.small,
            color =
                when {
                    selected -> c.onAccent
                    !enabled -> c.muted.copy(alpha = 0.5f)
                    else -> c.text
                },
        )
    }
}

@Composable
fun IconChip(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color? = null,
    size: Dp = 34.dp,
    description: String? = null,
) {
    val c = LocalBruma.current
    Box(
        Modifier
            .size(size)
            .glass(RoundedCornerShape(11.dp), strong = true)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint ?: c.text, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = LocalBruma.current
    Row(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) c.accent else c.track)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) c.onAccent else c.muted, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = T.label, color = if (enabled) c.onAccent else c.muted)
    }
}

@Composable
fun ProgressLine(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 5.dp,
) {
    val c = LocalBruma.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(c.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(height))
                .background(color),
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val c = LocalBruma.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.h2, color = c.text, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = T.small,
                color = c.accent,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAction() }
                        .padding(6.dp),
            )
        }
    }
}

@Composable
fun Badge(
    text: String,
    color: Color? = null,
) {
    val c = LocalBruma.current
    val col = color ?: c.accent
    Text(
        text,
        style = T.tiny,
        color = col,
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(col.copy(alpha = 0.14f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalBruma.current
    Column(
        modifier
            .fillMaxWidth()
            .glass()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = T.title, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(text, style = T.small, color = c.muted)
    }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
) {
    val c = LocalBruma.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.title, color = if (highlight) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = T.tiny, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    label: String? = null,
    initial2: String? = null,
    label2: String? = null,
    onConfirm2: ((String, String) -> Unit)? = null,
) {
    var value by remember { mutableStateOf(initial) }
    var value2 by remember { mutableStateOf(initial2 ?: "") }
    val c = LocalBruma.current
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, cursorColor = c.accent)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = T.h2) },
        text = {
            Column {
                OutlinedTextField(
                    value,
                    { value = it },
                    singleLine = true,
                    label =
                        if (label !=
                            null
                        ) {
                            { Text(label) }
                        } else {
                            null
                        },
                    colors = colors,
                )
                if (initial2 != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value2,
                        { value2 = it },
                        singleLine = true,
                        label =
                            if (label2 !=
                                null
                            ) {
                                { Text(label2) }
                            } else {
                                null
                            },
                        colors = colors,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (onConfirm2 != null) onConfirm2(value, value2) else onConfirm(value)
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val c = LocalBruma.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = T.h2) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(i) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioDot(i == selected)
                        Spacer(Modifier.width(12.dp))
                        Text(o, style = T.body, color = c.text)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
fun RadioDot(on: Boolean) {
    val c = LocalBruma.current
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(if (on) 5.dp else 2.dp, if (on) c.accent else c.muted, CircleShape),
    )
}

// ------------------------------------------------------------ formato de números

private val es = Locale("es", "CO")

fun fmtBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(es, "%.1f GB", gb)
        mb >= 100 -> String.format(es, "%.0f MB", mb)
        mb >= 1 -> String.format(es, "%.1f MB", mb)
        else -> String.format(es, "%.0f KB", kb)
    }
}

fun fmtDuration(sec: Long): String {
    if (sec <= 0) return "0:00"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(es, "%d:%02d:%02d", h, m, s) else String.format(es, "%d:%02d", m, s)
}

fun fmtEta(sec: Long): String =
    when {
        sec < 0 -> ""
        sec < 60 -> "$sec s"
        sec < 3600 -> "${sec / 60} min"
        else -> "${sec / 3600} h ${(sec % 3600) / 60} min"
    }
