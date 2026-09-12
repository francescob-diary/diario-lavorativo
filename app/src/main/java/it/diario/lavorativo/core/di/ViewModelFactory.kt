package it.diario.lavorativo.core.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import it.diario.lavorativo.DiarioApplication

/** Scorciatoia per recuperare il contenitore dalle schermate Compose. */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return (context.applicationContext as DiarioApplication).container
}

/** Recupera il contenitore dentro le factory dei ViewModel. */
val CreationExtras.diarioContainer: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DiarioApplication).container

/**
 * Il contenitore fuori da Compose e fuori dai ViewModel.
 *
 * Serve ai BroadcastReceiver, che vengono creati dal sistema e non hanno
 * ne' schermata ne' CreationExtras: hanno solo il Context che gli passa
 * Android.
 */
fun appContainer(context: android.content.Context): AppContainer =
    (context.applicationContext as DiarioApplication).container
