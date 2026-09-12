package it.diario.lavorativo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BluCantiere,
    onPrimary = Color.White,
    primaryContainer = BluCantiereChiaro,
    onPrimaryContainer = BluCantiereScuro,
    secondary = GialloCantiere,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFE0A6),
    onSecondaryContainer = GialloCantiereScuro,
    tertiary = VerdeAttivo,
    onTertiary = Color.White,
    error = RossoStop,
    onError = Color.White,
    background = GrigioSfondo,
    onBackground = GrigioTesto,
    surface = GrigioSuperficie,
    onSurface = GrigioTesto,
    surfaceVariant = Color(0xFFE2E5EA),
    onSurfaceVariant = GrigioTestoTenue,
    outline = Color(0xFF74777F)
)

private val DarkColors = darkColorScheme(
    primary = BluCantiereChiaro,
    onPrimary = BluCantiereScuro,
    primaryContainer = BluCantiereScuro,
    onPrimaryContainer = BluCantiereChiaro,
    secondary = GialloCantiere,
    onSecondary = Color.Black,
    secondaryContainer = GialloCantiereScuro,
    onSecondaryContainer = Color(0xFFFFE0A6),
    tertiary = VerdeAttivoChiaro,
    onTertiary = Color(0xFF00391B),
    error = RossoStopChiaro,
    onError = Color(0xFF601410),
    background = ScuroSfondo,
    onBackground = ScuroTesto,
    surface = ScuroSuperficie,
    onSurface = ScuroTesto,
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199)
)

/**
 * Tema dell'app. Il colore dinamico di Android 12+ e' volutamente disattivato:
 * il contrasto in esterno deve restare prevedibile.
 */
@Composable
fun DiarioLavorativoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DiarioTypography,
        content = content
    )
}
