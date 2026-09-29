package it.diario.lavorativo.ui.navigation

import it.diario.lavorativo.ui.history.DaySummaryScreen
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import it.diario.lavorativo.ui.dictation.DictationScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import it.diario.lavorativo.domain.model.SharedText
import it.diario.lavorativo.ui.entries.EntriesScreen
import it.diario.lavorativo.ui.backup.BackupScreen
import it.diario.lavorativo.ui.export.ExportScreen
import it.diario.lavorativo.ui.voice.VoiceNotesScreen
import it.diario.lavorativo.ui.vehicle.VehicleScreen
import it.diario.lavorativo.ui.history.DayDetailScreen
import it.diario.lavorativo.ui.history.HistoryScreen
import it.diario.lavorativo.ui.photos.PhotosScreen
import it.diario.lavorativo.ui.sites.SiteEditScreen
import it.diario.lavorativo.ui.weekly.WeeklyScreen
import it.diario.lavorativo.ui.sites.SitesScreen
import it.diario.lavorativo.ui.settings.SettingsScreen
import it.diario.lavorativo.ui.today.TodayScreen

/** Struttura di navigazione dell'app: barra in basso + NavHost. */
@Composable
fun DiarioNavHost(
    sharedText: SharedText? = null,
    onSharedTextHandled: () -> Unit = {},
    openWeeklyReport: Boolean = false,
    onWeeklyReportOpened: () -> Unit = {},
    openDictation: Boolean = false,
    onDictationOpened: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: DiarioDestination.OGGI.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Barra bassa: icone sole, la scritta compare solo sulla sezione
            // in cui ci si trova. Lo spazio risparmiato va alle schermate.
            // Lo spazio dei tasti di sistema si aggiunge fuori, cosi' i 64dp
            // restano tutti per le icone.
            NavigationBar(
                modifier = Modifier.navigationBarsPadding().height(64.dp),
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                DiarioDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route ||
                            (destination == DiarioDestination.CANTIERI &&
                                currentRoute.startsWith(SITE_EDIT_ROUTE_PREFIX)) ||
                            // Esportando, la scheda Impostazioni resta accesa:
                            // e' da li' che ci si arriva.
                            (destination == DiarioDestination.IMPOSTAZIONI &&
                                (currentRoute == EXPORT_ROUTE ||
                                    currentRoute == BACKUP_ROUTE ||
                                    currentRoute == VEHICLE_ROUTE)) ||
                            (destination == DiarioDestination.CALENDARIO &&
                                (currentRoute.startsWith(DAY_ROUTE_PREFIX) ||
                                    currentRoute.startsWith(SUMMARY_ROUTE_PREFIX) ||
                                    currentRoute.startsWith(ENTRIES_ROUTE_PREFIX) ||
                                    currentRoute.startsWith(PHOTOS_ROUTE_PREFIX) ||
                                    currentRoute.startsWith(VOICE_ROUTE_PREFIX) ||
                                    currentRoute == WEEKLY_ROUTE)),
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = {
                            Text(
                                destination.label,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        alwaysShowLabel = false
                    )
                }
            }
        }
    ) { innerPadding ->
        // Un testo condiviso da un'altra app apre subito la scheda comunicazione
        // sulla giornata di oggi, senza far navigare l'utente a mano.
        // Arrivo dalla notifica del lunedi': si apre subito il rapportino.
        LaunchedEffect(openWeeklyReport) {
            if (openWeeklyReport) {
                navController.navigate(WEEKLY_ROUTE)
                onWeeklyReportOpened()
            }
        }

        // Arrivo dalla notifica di fine giornata: si apre la dettatura di oggi.
        LaunchedEffect(openDictation) {
            if (openDictation) {
                val today = java.time.LocalDate.now().toEpochDay()
                navController.navigate(DICTATION_ROUTE_PREFIX + today.toString())
                onDictationOpened()
            }
        }

        LaunchedEffect(sharedText) {
            if (sharedText != null) {
                val today = java.time.LocalDate.now().toEpochDay()
                navController.navigate(ENTRIES_ROUTE_PREFIX + today.toString())
            }
        }

        NavHost(
            navController = navController,
            startDestination = DiarioDestination.OGGI.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(DiarioDestination.OGGI.route) {
                TodayScreen(
                    onOpenEntries = { date ->
                        navController.navigate(ENTRIES_ROUTE_PREFIX + date.toEpochDay().toString())
                    },
                    onOpenPhotos = { date ->
                        navController.navigate(PHOTOS_ROUTE_PREFIX + date.toEpochDay().toString())
                    },
                    onOpenVoiceNotes = { date ->
                        navController.navigate(VOICE_ROUTE_PREFIX + date.toEpochDay().toString())
                    },
                    onOpenDictation = { date ->
                        navController.navigate(DICTATION_ROUTE_PREFIX + date.toEpochDay().toString())
                    }
                )
            }
            composable(
                route = DICTATION_ROUTE,
                arguments = listOf(navArgument(DAY_ARG) { type = NavType.LongType })
            ) { entry ->
                DictationScreen(
                    epochDay = entry.arguments?.getLong(DAY_ARG) ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = {
                        navController.navigate(DiarioDestination.IMPOSTAZIONI.route)
                    }
                )
            }
            composable(DiarioDestination.CALENDARIO.route) {
                HistoryScreen(
                    // Dal calendario si vede la giornata compilata; per
                    // cambiarla c'e' MODIFICA dentro il riepilogo.
                    onOpenDay = { date ->
                        navController.navigate(SUMMARY_ROUTE_PREFIX + date.toEpochDay().toString())
                    },
                    onOpenWeekly = { navController.navigate(WEEKLY_ROUTE) }
                )
            }
            composable(
                route = DAY_ROUTE,
                arguments = listOf(navArgument(DAY_ARG) { type = NavType.LongType })
            ) { entry ->
                val epochDay = entry.arguments?.getLong(DAY_ARG) ?: 0L
                DayDetailScreen(
                    epochDay = epochDay,
                    onBack = { navController.popBackStack() },
                    onOpenEntries = {
                        navController.navigate(ENTRIES_ROUTE_PREFIX + epochDay.toString())
                    },
                    onOpenPhotos = {
                        navController.navigate(PHOTOS_ROUTE_PREFIX + epochDay.toString())
                    },
                    onOpenVoiceNotes = {
                        navController.navigate(VOICE_ROUTE_PREFIX + epochDay.toString())
                    },
                    onOpenDictation = {
                        navController.navigate(DICTATION_ROUTE_PREFIX + epochDay.toString())
                    }
                )
            }
            composable(
                route = ENTRIES_ROUTE,
                arguments = listOf(navArgument(DAY_ARG) { type = NavType.LongType })
            ) { entry ->
                val epochDay = entry.arguments?.getLong(DAY_ARG) ?: 0L
                val today = java.time.LocalDate.now().toEpochDay()
                EntriesScreen(
                    epochDay = epochDay,
                    onBack = { navController.popBackStack() },
                    // Il testo condiviso riguarda sempre la giornata di oggi.
                    sharedText = sharedText.takeIf { epochDay == today },
                    onSharedTextHandled = onSharedTextHandled
                )
            }
            composable(
                route = PHOTOS_ROUTE,
                arguments = listOf(navArgument(DAY_ARG) { type = NavType.LongType })
            ) { entry ->
                val epochDay = entry.arguments?.getLong(DAY_ARG) ?: 0L
                PhotosScreen(
                    epochDay = epochDay,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(WEEKLY_ROUTE) {
                WeeklyScreen(
                    onOpenDay = { date ->
                        navController.navigate(SUMMARY_ROUTE_PREFIX + date.toEpochDay().toString())
                    }
                )
            }
            composable(
                route = SUMMARY_ROUTE,
                arguments = listOf(navArgument(DAY_ARG) { type = NavType.LongType })
            ) { entry ->
                val epochDay = entry.arguments?.getLong(DAY_ARG) ?: 0L
                DaySummaryScreen(
                    epochDay = epochDay,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(DAY_ROUTE_PREFIX + epochDay.toString()) },
                    onOpenPhotos = { navController.navigate(PHOTOS_ROUTE_PREFIX + epochDay.toString()) }
                )
            }
            composable(DiarioDestination.CANTIERI.route) {
                SitesScreen(
                    onOpenSite = { siteId ->
                        navController.navigate(SITE_EDIT_ROUTE_PREFIX + siteId.toString())
                    },
                    onOpenDay = { date ->
                        navController.navigate(SUMMARY_ROUTE_PREFIX + date.toEpochDay().toString())
                    }
                )
            }
            composable(
                route = SITE_EDIT_ROUTE,
                arguments = listOf(navArgument(SITE_ID_ARG) { type = NavType.LongType })
            ) { entry ->
                val siteId = entry.arguments?.getLong(SITE_ID_ARG) ?: 0L
                SiteEditScreen(
                    siteId = siteId,
                    onDone = { navController.popBackStack() }
                )
            }
            composable(DiarioDestination.IMPOSTAZIONI.route) {
                SettingsScreen(
                    onOpenExport = { navController.navigate(EXPORT_ROUTE) },
                    onOpenBackup = { navController.navigate(BACKUP_ROUTE) },
                    onOpenVehicle = { navController.navigate(VEHICLE_ROUTE) }
                )
            }
            composable(EXPORT_ROUTE) {
                ExportScreen(onBack = { navController.popBackStack() })
            }
            composable(BACKUP_ROUTE) {
                BackupScreen(onBack = { navController.popBackStack() })
            }
            composable(VEHICLE_ROUTE) {
                VehicleScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = VOICE_ROUTE,
                arguments = listOf(navArgument(DAY_ARG) { type = NavType.LongType })
            ) { entry ->
                VoiceNotesScreen(
                    epochDay = entry.arguments?.getLong(DAY_ARG) ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private const val SITE_ID_ARG = "siteId"
private const val SITE_EDIT_ROUTE_PREFIX = "cantiere/"
private const val SITE_EDIT_ROUTE = SITE_EDIT_ROUTE_PREFIX + "{" + SITE_ID_ARG + "}"

private const val DAY_ARG = "epochDay"
private const val DAY_ROUTE_PREFIX = "giornata/"
private const val DAY_ROUTE = DAY_ROUTE_PREFIX + "{" + DAY_ARG + "}"

private const val WEEKLY_ROUTE = "rapportino"
private const val EXPORT_ROUTE = "esporta"
private const val BACKUP_ROUTE = "backup"
private const val VEHICLE_ROUTE = "mezzo"
private const val VOICE_ROUTE_PREFIX = "note/"
private const val VOICE_ROUTE = VOICE_ROUTE_PREFIX + "{" + DAY_ARG + "}"
private const val PHOTOS_ROUTE_PREFIX = "foto/"
private const val PHOTOS_ROUTE = PHOTOS_ROUTE_PREFIX + "{" + DAY_ARG + "}"
private const val ENTRIES_ROUTE_PREFIX = "voci/"
private const val DICTATION_ROUTE_PREFIX = "dettatura/"
private const val SUMMARY_ROUTE_PREFIX = "riepilogo/"
private const val SUMMARY_ROUTE = SUMMARY_ROUTE_PREFIX + "{" + DAY_ARG + "}"
private const val DICTATION_ROUTE = DICTATION_ROUTE_PREFIX + "{" + DAY_ARG + "}"
private const val ENTRIES_ROUTE = ENTRIES_ROUTE_PREFIX + "{" + DAY_ARG + "}"
