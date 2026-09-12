package it.diario.lavorativo.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.ui.graphics.vector.ImageVector

/** Sezioni principali dell'app (requisito 23). */
enum class DiarioDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    OGGI("oggi", "Oggi", Icons.Filled.Home),
    CALENDARIO("calendario", "Calendario", Icons.Filled.CalendarMonth),
    STATISTICHE("statistiche", "Statistiche", Icons.Filled.Insights),
    CANTIERI("cantieri", "Cantieri", Icons.Outlined.Domain),
    IMPOSTAZIONI("impostazioni", "Impostazioni", Icons.Filled.Settings)
}
