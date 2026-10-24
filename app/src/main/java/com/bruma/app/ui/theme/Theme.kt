package com.bruma.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.bruma.app.R

/** Colores de Bruma: fondo con manchas de color y paneles de vidrio translúcido. */
data class BrumaColors(
    val dark: Boolean,
    val bg: Color,
    val blobs: List<Color>,
    val glass: Color,
    val glassStrong: Color,
    val solid: Color,
    val border: Color,
    val selected: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val ok: Color,
    val warn: Color,
    val err: Color,
    val track: Color,
    val sheet: Color,
)

val LightBruma =
    BrumaColors(
        dark = false,
        bg = Color(0xFFEEF1F7),
        blobs = listOf(Color(0xFFB9D2FF), Color(0xFFD8C8FF), Color(0xFFFFDCE9), Color(0xFFC6F0E4)),
        glass = Color(0x9EFFFFFF),
        glassStrong = Color(0xD1FFFFFF),
        solid = Color(0xFFFFFFFF),
        border = Color(0xE6FFFFFF),
        selected = Color(0xFFFFFFFF),
        text = Color(0xFF121826),
        muted = Color(0xFF5B6475),
        accent = Color(0xFF4B5BFF),
        accentSoft = Color(0x1F4B5BFF),
        ok = Color(0xFF13A58E),
        warn = Color(0xFFE38B2C),
        err = Color(0xFFE5484D),
        track = Color(0x14121826),
        sheet = Color(0xF2F8F9FC),
    )

val DarkBruma =
    BrumaColors(
        dark = true,
        bg = Color(0xFF0B0F1A),
        blobs = listOf(Color(0xFF26357A), Color(0xFF3E2A6E), Color(0xFF4A2140), Color(0xFF0F4A40)),
        glass = Color(0x12FFFFFF),
        glassStrong = Color(0x1CFFFFFF),
        solid = Color(0xFF161B29),
        border = Color(0x1FFFFFFF),
        selected = Color(0x2EFFFFFF),
        text = Color(0xFFEEF1F8),
        muted = Color(0xFF9AA3B5),
        accent = Color(0xFF8B98FF),
        accentSoft = Color(0x298B98FF),
        ok = Color(0xFF3CCFB4),
        warn = Color(0xFFF2A85B),
        err = Color(0xFFFF6B6F),
        track = Color(0x1FFFFFFF),
        sheet = Color(0xF2141926),
    )

val LocalBruma = staticCompositionLocalOf { LightBruma }
val LocalSolid = staticCompositionLocalOf { false }

val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

/** Estilos de texto usados en toda la app. */
object T {
    val h1 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.6).sp)
    val h2 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.2).sp)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    val tiny = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val overline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp)
    val big = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.4).sp)
}

private fun TextStyle.inter() = copy(fontFamily = Inter)

private val BaseTypography = Typography()
private val BrumaTypography =
    Typography(
        displayLarge = BaseTypography.displayLarge.inter(),
        displayMedium = BaseTypography.displayMedium.inter(),
        displaySmall = BaseTypography.displaySmall.inter(),
        headlineLarge = BaseTypography.headlineLarge.inter(),
        headlineMedium = BaseTypography.headlineMedium.inter(),
        headlineSmall = BaseTypography.headlineSmall.inter(),
        titleLarge = BaseTypography.titleLarge.inter(),
        titleMedium = BaseTypography.titleMedium.inter(),
        titleSmall = BaseTypography.titleSmall.inter(),
        bodyLarge = BaseTypography.bodyLarge.inter(),
        bodyMedium = BaseTypography.bodyMedium.inter(),
        bodySmall = BaseTypography.bodySmall.inter(),
        labelLarge = BaseTypography.labelLarge.inter(),
        labelMedium = BaseTypography.labelMedium.inter(),
        labelSmall = BaseTypography.labelSmall.inter(),
    )

@Composable
fun BrumaTheme(
    theme: String,
    reduceTransparency: Boolean,
    content: @Composable () -> Unit,
) {
    val dark =
        when (theme) {
            "LIGHT" -> false
            "DARK" -> true
            else -> isSystemInDarkTheme()
        }
    val c = if (dark) DarkBruma else LightBruma
    val scheme =
        if (dark) {
            darkColorScheme(
                primary = c.accent,
                onPrimary = Color.White,
                secondary = c.ok,
                error = c.err,
                background = c.bg,
                onBackground = c.text,
                surface = Color(0xFF151A27),
                onSurface = c.text,
                surfaceVariant = Color(0xFF1C2232),
                onSurfaceVariant = c.muted,
                surfaceContainerLowest = Color(0xFF10141F),
                surfaceContainerLow = Color(0xFF141926),
                surfaceContainer = Color(0xFF181D2B),
                surfaceContainerHigh = Color(0xFF1D2333),
                surfaceContainerHighest = Color(0xFF232A3B),
                outline = Color(0x33FFFFFF),
            )
        } else {
            lightColorScheme(
                primary = c.accent,
                onPrimary = Color.White,
                secondary = c.ok,
                error = c.err,
                background = c.bg,
                onBackground = c.text,
                surface = Color(0xFFF8F9FC),
                onSurface = c.text,
                surfaceVariant = Color(0xFFE9ECF4),
                onSurfaceVariant = c.muted,
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color(0xFFF8F9FC),
                surfaceContainer = Color(0xFFF3F5FA),
                surfaceContainerHigh = Color(0xFFF7F8FC),
                surfaceContainerHighest = Color(0xFFE9ECF4),
                outline = Color(0x22121826),
            )
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalBruma provides c, LocalSolid provides reduceTransparency) {
        MaterialTheme(colorScheme = scheme, typography = BrumaTypography, content = content)
    }
}
