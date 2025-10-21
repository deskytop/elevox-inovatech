package com.elevox.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
	val autoDetectionEnabled: Boolean = true,
	val manualFloor: Int = 0, // 0 = Térreo, 1 = 1°, 2 = 2°, 3 = 3°
	val isSaving: Boolean = false
)

class SettingsViewModel(
	context: Context
) : ViewModel() {

	private val _state = MutableStateFlow(SettingsUiState())
	val state: StateFlow<SettingsUiState> = _state

	private val prefs: SharedPreferences = context.getSharedPreferences("elevox_settings", Context.MODE_PRIVATE)

	companion object {
		private const val AUTO_DETECTION_KEY = "auto_detection_enabled"
		private const val MANUAL_FLOOR_KEY = "manual_floor"
	}

	init {
		loadSettings()
	}

	/**
	 * Carrega as configurações salvas do SharedPreferences
	 */
	private fun loadSettings() {
		_state.value = SettingsUiState(
			autoDetectionEnabled = prefs.getBoolean(AUTO_DETECTION_KEY, true),
			manualFloor = prefs.getInt(MANUAL_FLOOR_KEY, 0)
		)
	}

	/**
	 * Alterna entre detecção automática e manual
	 */
	fun toggleAutoDetection(enabled: Boolean) {
		_state.value = _state.value.copy(
			autoDetectionEnabled = enabled,
			isSaving = true
		)

		viewModelScope.launch {
			prefs.edit().putBoolean(AUTO_DETECTION_KEY, enabled).apply()
			_state.value = _state.value.copy(isSaving = false)
		}
	}

	/**
	 * Define o andar manual quando detecção automática está desativada
	 */
	fun setManualFloor(floor: Int) {
		_state.value = _state.value.copy(
			manualFloor = floor,
			isSaving = true
		)

		viewModelScope.launch {
			prefs.edit().putInt(MANUAL_FLOOR_KEY, floor).apply()
			_state.value = _state.value.copy(isSaving = false)
		}
	}

	/**
	 * Retorna o andar atual baseado na configuração
	 * (manual ou automático)
	 */
	fun getCurrentFloor(): Int {
		return if (_state.value.autoDetectionEnabled) {
			// TODO: Implementar detecção Bluetooth aqui no futuro
			// Por enquanto retorna o manual
			_state.value.manualFloor
		} else {
			_state.value.manualFloor
		}
	}
}
