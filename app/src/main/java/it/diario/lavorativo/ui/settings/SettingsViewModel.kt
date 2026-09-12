package it.diario.lavorativo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.diario.lavorativo.core.di.diarioContainer
import it.diario.lavorativo.domain.model.UserSettings
import it.diario.lavorativo.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ViewModel delle impostazioni: pochi valori, salvati subito su DataStore. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setUserName(name: String) = viewModelScope.launch {
        settingsRepository.setUserName(name)
    }

    /** L'orario standard si regola a passi di 15 minuti, fra 1 e 16 ore. */
    fun changeStandardMinutes(deltaMinutes: Int) = viewModelScope.launch {
        val current = settings.value.standardWorkMinutes
        settingsRepository.setStandardWorkMinutes((current + deltaMinutes).coerceIn(60, 16 * 60))
    }

    fun setOvertimeEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setOvertimeEnabled(enabled)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(this.diarioContainer.settingsRepository)
            }
        }
    }
}
