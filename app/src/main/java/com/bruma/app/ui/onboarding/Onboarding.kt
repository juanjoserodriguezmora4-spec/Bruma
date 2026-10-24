package com.bruma.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bruma.app.ui.common.BI
import com.bruma.app.ui.common.GlassBackground
import com.bruma.app.ui.common.PrimaryButton
import com.bruma.app.ui.common.glass
import com.bruma.app.ui.theme.LocalBruma
import com.bruma.app.ui.theme.T

private data class Page(
    val icon: ImageVector,
    val title: String,
    val text: String,
)

private val pages =
    listOf(
        Page(
            BI.Download,
            "Descarga en tres toques",
            "Pega un enlace, elige la calidad y listo. Bruma guarda vídeos y música con su carátula, ordenados en tu biblioteca.",
        ),
        Page(
            BI.Link,
            "Desde cualquier app",
            "En YouTube, Vimeo, SoundCloud o el navegador, toca «Compartir» y elige Bruma. También detectamos enlaces que copies.",
        ),
        Page(
            BI.Shield,
            "Úsala con responsabilidad",
            "Descarga solo contenido propio, libre o que tengas permiso de guardar. Bruma no rompe protecciones DRM, no usa cuentas y no envía tus datos a ningún lado.",
        ),
    )

@Composable
fun Onboarding(onDone: () -> Unit) {
    val c = LocalBruma.current
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[index]
    val last = index == pages.lastIndex

    GlassBackground(seed = index) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text("bruma", style = T.big, color = c.text, modifier = Modifier.weight(1f))
                if (!last) {
                    Text(
                        "Saltar",
                        style = T.label,
                        color = c.muted,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDone() }
                                .padding(6.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(96.dp)
                    .glass(RoundedCornerShape(28.dp), strong = true),
                contentAlignment = Alignment.Center,
            ) {
                Icon(page.icon, null, tint = c.accent, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(page.title, style = T.h1, color = c.text)
            Spacer(Modifier.height(12.dp))
            Text(page.text, style = T.body, color = c.muted)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 18.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(width = if (i == index) 22.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (i == index) c.accent else c.track),
                    )
                }
            }
            PrimaryButton(
                text = if (last) "Entendido, empezar" else "Siguiente",
                onClick = { if (last) onDone() else index++ },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
